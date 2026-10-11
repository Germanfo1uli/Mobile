import { randomInt } from "node:crypto";
import { GameError } from "./game.js";
import { requireUuid, ValidationError } from "./validation.js";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function matchCode() {
  return Array.from({ length: 6 }, () => CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]).join("");
}

function validateScore(body = {}) {
  const fields = ["score", "hits", "misses"];
  const errors = {};
  for (const field of fields) {
    if (!Number.isInteger(body[field]) || body[field] < 0 || body[field] > 1_000_000_000) errors[field] = "Must be a non-negative integer";
  }
  if (Object.keys(errors).length) throw new ValidationError(errors);
  return { score: body.score, hits: body.hits, misses: body.misses };
}

async function getMatch(client, code, lock = false) {
  const query = `SELECT m.*, p.full_name AS host_name
                 FROM multiplayer_matches m JOIN players p ON p.id = m.host_player_id
                 WHERE m.code = $1${lock ? " FOR UPDATE" : ""}`;
  const result = await client.query(query, [String(code).trim().toUpperCase()]);
  if (!result.rowCount) throw new GameError(404, "Match not found");
  return result.rows[0];
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
  const ranked = [...rows].filter((row) => row.submitted).sort((a, b) => b.score - a.score || String(a.submittedAt).localeCompare(String(b.submittedAt)));
  ranked.forEach((row, index) => { row.place = index + 1; });
  return {
    code: match.code,
    status: match.status,
    hostPlayerId: match.host_player_id,
    hostName: match.host_name,
    maxPlayers: match.max_players,
    createdAt: match.created_at,
    startedAt: match.started_at,
    finishedAt: match.finished_at,
    players: rows,
    results: ranked,
    winner: ranked[0] ?? null,
  };
}

export function registerMultiplayerRoutes(app, { inTransaction }) {
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
    const match = await inTransaction(async (client) => snapshot(client, await getMatch(client, request.params.code)));
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
    const result = validateScore(request.body);
    const match = await inTransaction(async (client) => {
      const current = await getMatch(client, request.params.code, true);
      if (current.status !== "running") throw new GameError(409, "Match is not running");
      const member = await client.query("SELECT 1 FROM multiplayer_match_players WHERE match_id = $1 AND player_id = $2", [current.id, playerId]);
      if (!member.rowCount) throw new GameError(403, "Player is not in this match");
      await client.query(
        `INSERT INTO multiplayer_match_results(match_id, player_id, score, hits, misses)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (match_id, player_id) DO UPDATE SET score = EXCLUDED.score, hits = EXCLUDED.hits, misses = EXCLUDED.misses, submitted_at = now()`,
        [current.id, playerId, result.score, result.hits, result.misses],
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
