import { GAME_RULES, TARGET_TYPES, GameError, advanceRound, applyEvent, createRoundState, roundSnapshot, validateEvent } from "./game.js";
import { requireUuid } from "./validation.js";

async function finishRound(client, round, now) {
  if (round.status === "active") {
    round.status = "finished";
    await client.query(
      "UPDATE game_rounds SET status = 'finished', state = $2, finished_at = $3 WHERE id = $1",
      [round.id, round.state, new Date(Math.min(now, round.state.endsAt))],
    );
    await client.query(
      `INSERT INTO game_results(player_id, score, hits, misses, difficulty, round_id, verified, played_at)
       VALUES ($1, $2, $3, $4, $5, $6, true, $7) ON CONFLICT (round_id) DO NOTHING`,
      [round.player_id, round.state.score, round.state.hits, round.state.misses, round.state.difficulty, round.id, new Date(Math.min(now, round.state.endsAt))],
    );
  }
  const result = await client.query(
    `SELECT id, round_id AS "roundId", player_id AS "playerId", score, hits, misses,
            difficulty, played_at AS "playedAt", verified FROM game_results WHERE round_id = $1`,
    [round.id],
  );
  return result.rows[0];
}

async function getLockedRound(client, roundId) {
  const result = await client.query("SELECT * FROM game_rounds WHERE id = $1 FOR UPDATE", [roundId]);
  if (!result.rowCount) throw new GameError(404, "Round not found");
  return result.rows[0];
}

async function refreshRound(client, round, now) {
  if (round.status === "finished") return finishRound(client, round, now);
  if (!advanceRound(round.state, now)) return finishRound(client, round, now);
  await client.query("UPDATE game_rounds SET state = $2 WHERE id = $1", [round.id, round.state]);
  return null;
}

// A restarted server can finish overdue rounds using the persisted server deadline.
export async function finalizeExpiredRounds(inTransaction) {
  return inTransaction(async (client) => {
    const now = Date.now();
    const rounds = await client.query(
      `SELECT * FROM game_rounds WHERE status = 'active' AND (state ->> 'endsAt')::bigint <= $1
       ORDER BY (state ->> 'endsAt')::bigint LIMIT 100 FOR UPDATE SKIP LOCKED`,
      [now],
    );
    for (const round of rounds.rows) await finishRound(client, round, now);
  });
}

export function registerRoundRoutes(app, { inTransaction }) {
  app.get("/api/game/catalog", (_request, response) => {
    response.json({
      coordinateSystem: "normalized_playfield",
      targets: TARGET_TYPES.map((type) => ({ ...type, assetKey: type.id })),
      rules: GAME_RULES,
      bonus: { type: "theurgy", assetKey: "theurgy_card", defaultIntervalSeconds: 15 },
      sound: { cue: GAME_RULES.soundCue, assetKey: GAME_RULES.soundCue },
    });
  });

  app.post("/api/rounds", async (request, response) => {
    const playerId = requireUuid(request.body?.playerId, "playerId");
    const payload = await inTransaction(async (client) => {
      // Serialize round creation per player; allow FK key-share locks on player inserts.
      const playerResult = await client.query("SELECT * FROM players WHERE id = $1 FOR NO KEY UPDATE", [playerId]);
      if (!playerResult.rowCount) throw new GameError(404, "Player not found");
      const now = Date.now();
      const existing = await client.query("SELECT * FROM game_rounds WHERE player_id = $1 AND status = 'active' FOR UPDATE", [playerId]);
      if (existing.rowCount) {
        const round = existing.rows[0];
        await refreshRound(client, round, now);
        if (round.status === "active") return { round: roundSnapshot(round, now), resumed: true };
      }
      const settingsResult = await client.query("SELECT * FROM player_settings WHERE player_id = $1", [playerId]);
      if (!settingsResult.rowCount) throw new GameError(409, "Player settings are missing");
      const settings = settingsResult.rows[0];
      const state = createRoundState(playerResult.rows[0], {
        gameSpeed: Number(settings.game_speed), maxInsects: settings.max_insects,
        bonusIntervalSeconds: settings.bonus_interval_seconds,
        roundDurationSeconds: settings.round_duration_seconds,
      }, now);
      const created = await client.query("INSERT INTO game_rounds(player_id, state) VALUES ($1, $2) RETURNING *", [playerId, state]);
      return { round: roundSnapshot(created.rows[0], now), resumed: false };
    });
    response.status(payload.resumed ? 200 : 201).json(payload);
  });

  app.get("/api/rounds/:roundId", async (request, response) => {
    const roundId = requireUuid(request.params.roundId, "roundId");
    const payload = await inTransaction(async (client) => {
      const round = await getLockedRound(client, roundId);
      const now = Date.now();
      const result = await refreshRound(client, round, now);
      return { round: roundSnapshot(round, now), result };
    });
    response.json(payload);
  });

  app.post("/api/rounds/:roundId/events", async (request, response) => {
    const roundId = requireUuid(request.params.roundId, "roundId");
    const event = validateEvent(request.body);
    const outcome = await inTransaction(async (client) => {
      const round = await getLockedRound(client, roundId);
      const previous = await client.query("SELECT request, response FROM round_events WHERE round_id = $1 AND event_id = $2", [roundId, event.eventId]);
      if (previous.rowCount) {
        const saved = previous.rows[0];
        const sameRequest = Object.keys(event).every((key) => saved.request[key] === event[key]);
        if (!sameRequest) throw new GameError(409, "eventId was already used for another event");
        return { status: 200, payload: saved.response };
      }
      const now = Date.now();
      const result = await refreshRound(client, round, now);
      if (round.status === "finished") {
        // Return rather than throw so expired rounds/results are committed.
        return { status: 409, payload: { error: "Round has finished", round: roundSnapshot(round, now), result } };
      }
      const eventResult = applyEvent(round.state, event, now);
      await client.query("UPDATE game_rounds SET state = $2 WHERE id = $1", [roundId, round.state]);
      const payload = { event: { eventId: event.eventId, ...eventResult }, round: roundSnapshot(round, now) };
      await client.query("INSERT INTO round_events(round_id, event_id, request, response) VALUES ($1, $2, $3, $4)", [roundId, event.eventId, event, payload]);
      return { status: 200, payload };
    });
    response.status(outcome.status).json(outcome.payload);
  });

  app.post("/api/rounds/:roundId/finish", async (request, response) => {
    const roundId = requireUuid(request.params.roundId, "roundId");
    const payload = await inTransaction(async (client) => {
      const round = await getLockedRound(client, roundId);
      const now = Date.now();
      const result = await finishRound(client, round, now);
      return { round: roundSnapshot(round, now), result };
    });
    response.json(payload);
  });
}
