import { randomInt } from "node:crypto";
import { GameError, createRoundState, roundSnapshot } from "./game.js";
import { requireUuid, ValidationError } from "./validation.js";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const QUICK_SEARCH_TIMEOUT_MS = 20_000;

function matchCode() {
  return Array.from({ length: 6 }, () => CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]).join("");
}

async function getMatch(client, code, lock = false) {
  const query = `SELECT m.*, p.full_name AS host_name
                 FROM multiplayer_matches m JOIN players p ON p.id = m.host_player_id
                 WHERE m.code = $1${lock ? " FOR UPDATE OF m" : ""}`;
  const result = await client.query(query, [String(code).trim().toUpperCase()]);
  if (!result.rowCount) throw new GameError(404, "Match not found");
  return result.rows[0];
}

async function expireQuickSearch(client, match) {
  if (match.mode !== "quick" || match.status !== "waiting") return match;
  if (Date.now() - new Date(match.created_at).getTime() < QUICK_SEARCH_TIMEOUT_MS) return match;
  const expired = await client.query(
    "UPDATE multiplayer_matches SET status = 'expired' WHERE id = $1 AND status = 'waiting' RETURNING *", [match.id],
  );
  return expired.rowCount ? { ...match, ...expired.rows[0] } : match;
}

async function snapshot(client, match) {
  const players = await client.query(
    `SELECT p.id, p.full_name, mp.joined_at,
            r.score, r.hits, r.misses, r.submitted_at
     FROM multiplayer_match_players mp
     JOIN players p ON p.id = mp.player_id
     LEFT JOIN multiplayer_match_results r ON r.match_id = mp.match_id AND r.player_id = mp.player_id
     WHERE mp.match_id = $1 ORDER BY mp.joined_at, p.full_name`, [match.id],
  );
  const rows = players.rows.map((row, index) => ({
    place: row.score === null ? null : index + 1,
    playerId: row.id,
    playerName: row.full_name,
    joinedAt: row.joined_at,
    submitted: row.score !== null,
    score: row.score === null ? null : Number(row.score),
    hits: row.hits === null ? null : Number(row.hits),
    misses: row.misses === null ? null : Number(row.misses),
    submittedAt: row.submitted_at,
  }));
  const ranked = [...rows].filter((row) => row.submitted).sort((a, b) => b.score - a.score || a.playerName.localeCompare(b.playerName));
  ranked.forEach((row, index) => { row.place = index > 0 && row.score === ranked[index - 1].score ? ranked[index - 1].place : index + 1; });
  const draw = match.status === "finished" && ranked.length > 1 && ranked[0].score === ranked[1].score;
  return {
    code: match.code,
    status: match.status,
    mode: match.mode,
    searchRemainingMilliseconds: match.mode === "quick" && match.status === "waiting"
      ? Math.max(0, QUICK_SEARCH_TIMEOUT_MS - (Date.now() - new Date(match.created_at).getTime())) : 0,
    hostPlayerId: match.host_player_id,
    hostName: match.host_name,
    maxPlayers: match.max_players,
    createdAt: match.created_at,
    startedAt: match.started_at,
    finishedAt: match.finished_at,
    players: rows,
    results: ranked,
    winner: match.status === "finished" && !draw ? ranked[0] ?? null : null,
    draw,
  };
}

// Called from the existing server-authoritative round finalizer, including timer expiry.
export async function recordMatchRoundResult(client, round) {
  const linked = await client.query(
    `SELECT m.id FROM multiplayer_matches m
     JOIN multiplayer_match_players mp ON mp.match_id = m.id
     WHERE mp.round_id = $1 AND mp.player_id = $2 AND m.status = 'running'
     FOR UPDATE OF m`, [round.id, round.player_id],
  );
  if (!linked.rowCount) return;
  const matchId = linked.rows[0].id;
  await client.query(
    `INSERT INTO multiplayer_match_results(match_id, player_id, round_id, score, hits, misses)
     VALUES ($1, $2, $3, $4, $5, $6)
     ON CONFLICT (match_id, player_id) DO NOTHING`,
    [matchId, round.player_id, round.id, round.state.score, round.state.hits, round.state.misses],
  );
  const count = await client.query(
    `SELECT count(*)::int AS total, count(r.player_id)::int AS submitted
     FROM multiplayer_match_players p
     LEFT JOIN multiplayer_match_results r ON r.match_id = p.match_id AND r.player_id = p.player_id
     WHERE p.match_id = $1`, [matchId],
  );
  if (count.rows[0].total === 2 && count.rows[0].submitted === 2) {
    await client.query("UPDATE multiplayer_matches SET status = 'finished', finished_at = now() WHERE id = $1", [matchId]);
  }
}

export function registerMultiplayerRoutes(app, { inTransaction }) {
  app.post("/api/matchmaking/search", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const match = await inTransaction(async (client) => {
      // One transaction at a time chooses a waiting opponent, so simultaneous taps cannot create two lobbies.
      await client.query("SELECT pg_advisory_xact_lock(473, 5)");
      const player = await client.query("SELECT id, full_name FROM players WHERE id = $1", [playerId]);
      if (!player.rowCount) throw new GameError(404, "Player not found");
      await client.query(
        `UPDATE multiplayer_matches SET status = 'expired'
         WHERE mode = 'quick' AND status = 'waiting' AND created_at <= now() - interval '20 seconds'`,
      );
      const existing = await client.query(
        `SELECT m.code FROM multiplayer_matches m
         JOIN multiplayer_match_players mp ON mp.match_id = m.id
         WHERE m.mode = 'quick' AND m.status IN ('waiting', 'running') AND mp.player_id = $1
         ORDER BY m.created_at DESC LIMIT 1`, [playerId],
      );
      if (existing.rowCount) return snapshot(client, await getMatch(client, existing.rows[0].code));
      const waiting = await client.query(
        `SELECT m.code FROM multiplayer_matches m
         WHERE m.mode = 'quick' AND m.status = 'waiting' AND m.host_player_id <> $1
         ORDER BY m.created_at LIMIT 1 FOR UPDATE`, [playerId],
      );
      if (waiting.rowCount) {
        const found = await getMatch(client, waiting.rows[0].code, true);
        await client.query("INSERT INTO multiplayer_match_players(match_id, player_id) VALUES ($1, $2)", [found.id, playerId]);
        const started = await client.query(
          "UPDATE multiplayer_matches SET status = 'running', started_at = now() WHERE id = $1 RETURNING *", [found.id],
        );
        return snapshot(client, { ...started.rows[0], host_name: found.host_name });
      }
      for (let attempt = 0; attempt < 5; attempt += 1) {
        const created = await client.query(
          `INSERT INTO multiplayer_matches(code, host_player_id, max_players, mode)
           VALUES ($1, $2, 2, 'quick') ON CONFLICT (code) DO NOTHING RETURNING *`,
          [matchCode(), playerId],
        );
        if (created.rowCount) {
          await client.query("INSERT INTO multiplayer_match_players(match_id, player_id) VALUES ($1, $2)", [created.rows[0].id, playerId]);
          return snapshot(client, { ...created.rows[0], host_name: player.rows[0].full_name });
        }
      }
      throw new GameError(503, "Unable to enter matchmaking queue");
    });
    response.json({ match });
  });

  app.post("/api/matchmaking/cancel", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const result = await inTransaction(async (client) => {
      await client.query("SELECT pg_advisory_xact_lock(473, 5)");
      return client.query(
        `DELETE FROM multiplayer_matches WHERE mode = 'quick' AND status = 'waiting' AND host_player_id = $1
         RETURNING code`, [playerId],
      );
    });
    response.json({ cancelled: result.rowCount > 0 });
  });

  app.post("/api/matches/:code/round", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const payload = await inTransaction(async (client) => {
      const match = await getMatch(client, request.params.code, true);
      const member = await client.query(
        "SELECT round_id FROM multiplayer_match_players WHERE match_id = $1 AND player_id = $2 FOR UPDATE",
        [match.id, playerId],
      );
      if (!member.rowCount) throw new GameError(403, "Player is not in this match");
      if (member.rows[0].round_id) {
        const saved = await client.query("SELECT * FROM game_rounds WHERE id = $1", [member.rows[0].round_id]);
        return { round: roundSnapshot(saved.rows[0], Date.now()), resumed: true };
      }
      if (match.status !== "running") throw new GameError(409, "Match is not running");
      const active = await client.query("SELECT id FROM game_rounds WHERE player_id = $1 AND status = 'active'", [playerId]);
      if (active.rowCount) throw new GameError(409, "Finish your current hunt before entering a match");
      const player = await client.query("SELECT * FROM players WHERE id = $1", [playerId]);
      if (!player.rowCount) throw new GameError(404, "Player not found");
      const settings = await client.query("SELECT * FROM player_settings WHERE player_id = $1", [playerId]);
      if (!settings.rowCount) throw new GameError(409, "Player settings are missing");
      const config = settings.rows[0];
      const state = createRoundState(player.rows[0], {
        gameSpeed: Number(config.game_speed), maxInsects: config.max_insects,
        bonusIntervalSeconds: config.bonus_interval_seconds,
        roundDurationSeconds: config.round_duration_seconds,
      }, Date.now(), { level: 1, selectedTheurgy: player.rows[0].selected_theurgy });
      const created = await client.query("INSERT INTO game_rounds(player_id, state) VALUES ($1, $2) RETURNING *", [playerId, state]);
      await client.query("UPDATE multiplayer_match_players SET round_id = $3 WHERE match_id = $1 AND player_id = $2", [match.id, playerId, created.rows[0].id]);
      return { round: roundSnapshot(created.rows[0], Date.now()), resumed: false };
    });
    response.status(payload.resumed ? 200 : 201).json(payload);
  });

  app.post("/api/matches", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const maxPlayers = request.body?.maxPlayers ?? 8;
    if (!Number.isInteger(maxPlayers) || maxPlayers < 2 || maxPlayers > 16) throw new ValidationError({ maxPlayers: "Must be an integer from 2 to 16" });
    const match = await inTransaction(async (client) => {
      const player = await client.query("SELECT id FROM players WHERE id = $1", [playerId]);
      if (!player.rowCount) throw new GameError(404, "Player not found");
      let code;
      for (let attempt = 0; attempt < 5; attempt += 1) {
        const candidate = matchCode();
        const created = await client.query(
          `INSERT INTO multiplayer_matches(code, host_player_id, max_players)
           VALUES ($1, $2, $3) ON CONFLICT (code) DO NOTHING RETURNING *`, [candidate, playerId, maxPlayers],
        );
        if (created.rowCount) { code = created.rows[0]; break; }
      }
      if (!code) throw new GameError(503, "Unable to create match code");
      await client.query("INSERT INTO multiplayer_match_players(match_id, player_id) VALUES ($1, $2)", [code.id, playerId]);
      return snapshot(client, code);
    });
    response.status(201).json({ match });
  });

  app.get("/api/matches/:code", async (request, response) => {
    const match = await inTransaction(async (client) => {
      const current = await getMatch(client, request.params.code, true);
      return snapshot(client, await expireQuickSearch(client, current));
    });
    response.json({ match });
  });

  app.post("/api/matches/:code/players", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const match = await inTransaction(async (client) => {
      const current = await getMatch(client, request.params.code, true);
      const player = await client.query("SELECT id FROM players WHERE id = $1", [playerId]);
      if (!player.rowCount) throw new GameError(404, "Player not found");
      if (current.status !== "waiting") throw new GameError(409, "Match has already started");
      const count = await client.query("SELECT count(*)::int AS count FROM multiplayer_match_players WHERE match_id = $1", [current.id]);
      const alreadyJoined = await client.query("SELECT 1 FROM multiplayer_match_players WHERE match_id = $1 AND player_id = $2", [current.id, playerId]);
      if (!alreadyJoined.rowCount && count.rows[0].count >= current.max_players) throw new GameError(409, "Match is full");
      await client.query("INSERT INTO multiplayer_match_players(match_id, player_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [current.id, playerId]);
      return snapshot(client, current);
    });
    response.status(200).json({ match });
  });

  app.post("/api/matches/:code/start", async (request, response) => {
    const hostPlayerId = requireUuid(request.body?.playerId, "playerId");
    const match = await inTransaction(async (client) => {
      const current = await getMatch(client, request.params.code, true);
      if (current.host_player_id !== hostPlayerId) throw new GameError(403, "Only the host can start the match");
      if (current.status !== "waiting") return snapshot(client, current);
      const updated = await client.query("UPDATE multiplayer_matches SET status = 'running', started_at = now() WHERE id = $1 RETURNING *", [current.id]);
      return snapshot(client, updated.rows[0]);
    });
    response.json({ match });
  });

  app.post("/api/matches/:code/results", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const roundId = requireUuid(request.body?.roundId, "roundId");
    const match = await inTransaction(async (client) => {
      const current = await getMatch(client, request.params.code, true);
      const member = await client.query("SELECT 1 FROM multiplayer_match_players WHERE match_id = $1 AND player_id = $2", [current.id, playerId]);
      if (!member.rowCount) throw new GameError(403, "Player is not in this match");
      const prior = await client.query(
        "SELECT round_id FROM multiplayer_match_results WHERE match_id = $1 AND player_id = $2",
        [current.id, playerId],
      );
      if (prior.rowCount) {
        if (prior.rows[0].round_id !== roundId) throw new GameError(409, "A different round was already submitted");
        return snapshot(client, current);
      }
      if (current.status !== "running") throw new GameError(409, "Match is not running");
      const verified = await client.query(
        `SELECT r.round_id, r.score, r.hits, r.misses FROM game_results r
         JOIN game_rounds g ON g.id = r.round_id
         JOIN multiplayer_match_players mp ON mp.round_id = r.round_id AND mp.match_id = $3
         WHERE r.round_id = $1 AND r.player_id = $2 AND r.verified = true
         AND g.started_at >= $4`, [roundId, playerId, current.id, current.started_at],
      );
      if (!verified.rowCount) throw new GameError(409, "A finished verified hunt is required");
      const result = verified.rows[0];
      await client.query(
        `INSERT INTO multiplayer_match_results(match_id, player_id, round_id, score, hits, misses)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (match_id, player_id) DO NOTHING`,
        [current.id, playerId, result.round_id, result.score, result.hits, result.misses],
      );
      const counts = await client.query(
        `SELECT count(*)::int AS total, count(r.player_id)::int AS submitted
         FROM multiplayer_match_players p LEFT JOIN multiplayer_match_results r ON r.match_id = p.match_id AND r.player_id = p.player_id
         WHERE p.match_id = $1`, [current.id],
      );
      const final = counts.rows[0].total > 0 && counts.rows[0].total === counts.rows[0].submitted;
      const updated = final
        ? await client.query("UPDATE multiplayer_matches SET status = 'finished', finished_at = now() WHERE id = $1 RETURNING *", [current.id])
        : { rows: [current] };
      return snapshot(client, updated.rows[0]);
    });
    response.json({ match });
  });
}
