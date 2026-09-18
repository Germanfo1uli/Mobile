import cors from "cors";
import express from "express";
import { readFile } from "node:fs/promises";
import { ValidationError, requireUuid, validatePlayer, validateSettings } from "./validation.js";
import { GameError } from "./game.js";
import { registerRoundRoutes } from "./rounds.js";

function playerRow(row) {
  const birthDate = row.birth_date instanceof Date
    ? [row.birth_date.getFullYear(), String(row.birth_date.getMonth() + 1).padStart(2, "0"), String(row.birth_date.getDate()).padStart(2, "0")].join("-")
    : row.birth_date;
  return {
    id: row.id,
    fullName: row.full_name,
    gender: row.gender,
    course: row.course,
    difficulty: row.difficulty,
    birthDate,
    zodiac: row.zodiac,
    createdAt: row.created_at,
  };
}

function settingsRow(row) {
  return {
    playerId: row.player_id,
    gameSpeed: Number(row.game_speed),
    maxInsects: row.max_insects,
    bonusIntervalSeconds: row.bonus_interval_seconds,
    roundDurationSeconds: row.round_duration_seconds,
    updatedAt: row.updated_at,
  };
}

export function createApp({ pool, inTransaction, corsOrigin = "*" }) {
  const app = express();
  app.disable("x-powered-by");
  app.use(cors({ origin: corsOrigin === "*" ? true : corsOrigin.split(",").map((item) => item.trim()) }));
  app.use(express.json({ limit: "32kb" }));

  app.get("/api/health", async (_request, response) => {
    await pool.query("SELECT 1");
    response.json({ status: "ok" });
  });

  app.get("/api/game/rules", async (_request, response) => {
    const html = await readFile(new URL("../resources/game_rules.html", import.meta.url), "utf8");
    response.type("html").send(html);
  });

  app.get("/api/game/menu", (_request, response) => {
    response.json({
      actions: [
        { id: "register", method: "POST", path: "/api/players" },
        { id: "select_player", method: "GET", path: "/api/players" },
        { id: "start_or_resume", method: "POST", path: "/api/rounds" },
        { id: "settings", method: "GET", path: "/api/players/:playerId/settings" },
        { id: "rules", method: "GET", path: "/api/game/rules" },
        { id: "authors", method: "GET", path: "/api/game/authors" },
        { id: "records", method: "GET", path: "/api/records" },
      ],
    });
  });

  app.get("/api/game/authors", (_request, response) => {
    response.json({ authors: [
      { name: "Герман Пырцак", contribution: "Интерфейс, досье игрока и визуальная система" },
      { name: "Яна Карпова", contribution: "Backend, PostgreSQL и сетевое взаимодействие" },
    ] });
  });

  app.post("/api/players", async (request, response) => {
    const player = validatePlayer(request.body);
    const created = await inTransaction(async (client) => {
      const result = await client.query(
        `INSERT INTO players(full_name, gender, course, difficulty, birth_date, zodiac)
         VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING *`,
        [player.fullName, player.gender, player.course, player.difficulty, player.birthDate, player.zodiac],
      );
      await client.query("INSERT INTO player_settings(player_id) VALUES ($1)", [result.rows[0].id]);
      return result.rows[0];
    });
    response.status(201).json({ player: playerRow(created) });
  });

  app.get("/api/players", async (_request, response) => {
    const result = await pool.query("SELECT * FROM players ORDER BY created_at DESC, id DESC");
    response.json({ players: result.rows.map(playerRow) });
  });

  app.get("/api/players/:playerId", async (request, response) => {
    const playerId = requireUuid(request.params.playerId, "playerId");
    const result = await pool.query("SELECT * FROM players WHERE id = $1", [playerId]);
    if (!result.rowCount) return response.status(404).json({ error: "Player not found" });
    response.json({ player: playerRow(result.rows[0]) });
  });

  app.get("/api/players/:playerId/results", async (request, response) => {
    const playerId = requireUuid(request.params.playerId, "playerId");
    const player = await pool.query("SELECT 1 FROM players WHERE id = $1", [playerId]);
    if (!player.rowCount) return response.status(404).json({ error: "Player not found" });
    const limit = pageInteger(request.query.limit, 20, 1, 100);
    const offset = pageInteger(request.query.offset, 0, 0, 1_000_000);
    const result = await pool.query(
      `SELECT id, round_id AS "roundId", score, hits, misses, difficulty,
              played_at AS "playedAt", verified FROM game_results
       WHERE player_id = $1 ORDER BY played_at DESC, id DESC LIMIT $2 OFFSET $3`,
      [playerId, limit, offset],
    );
    response.json({ results: result.rows, limit, offset });
  });

  app.get("/api/players/:playerId/settings", async (request, response) => {
    const playerId = requireUuid(request.params.playerId, "playerId");
    const result = await pool.query("SELECT * FROM player_settings WHERE player_id = $1", [playerId]);
    if (!result.rowCount) return response.status(404).json({ error: "Player not found" });
    response.json({ settings: settingsRow(result.rows[0]) });
  });

  app.put("/api/players/:playerId/settings", async (request, response) => {
    const playerId = requireUuid(request.params.playerId, "playerId");
    const settings = validateSettings(request.body);
    const result = await pool.query(
      `UPDATE player_settings
       SET game_speed = $2, max_insects = $3, bonus_interval_seconds = $4,
           round_duration_seconds = $5, updated_at = now()
       WHERE player_id = $1
       RETURNING *`,
      [playerId, settings.gameSpeed, settings.maxInsects, settings.bonusIntervalSeconds, settings.roundDurationSeconds],
    );
    if (!result.rowCount) return response.status(404).json({ error: "Player not found" });
    response.json({ settings: settingsRow(result.rows[0]) });
  });

  app.post("/api/results", (_request, response) => {
    response.status(410).json({
      error: "Client-supplied scores are no longer accepted",
      replacement: "POST /api/rounds/:roundId/finish",
    });
  });

  registerRoundRoutes(app, { inTransaction });

  app.get("/api/records", async (request, response) => {
    const limit = pageInteger(request.query.limit, 20, 1, 100);
    const offset = pageInteger(request.query.offset, 0, 0, 1_000_000);
    const difficulty = request.query.difficulty === undefined ? null : pageInteger(request.query.difficulty, null, 1, 5);
    const result = await pool.query(
      `SELECT r.id, r.score, r.hits, r.misses, r.difficulty, r.played_at,
              p.id AS player_id, p.full_name
       FROM game_results r
       JOIN players p ON p.id = r.player_id
       WHERE r.verified = true AND ($3::smallint IS NULL OR r.difficulty = $3)
       ORDER BY r.score DESC, r.played_at ASC, r.id ASC
       LIMIT $1 OFFSET $2`,
      [limit, offset, difficulty],
    );
    response.json({ records: result.rows, limit, offset });
  });

  app.use((_request, response) => response.status(404).json({ error: "Route not found" }));
  app.use((error, _request, response, _next) => {
    if (error instanceof GameError) return response.status(error.status).json({ error: error.message });
    if (error instanceof ValidationError) return response.status(400).json({ error: error.message, details: error.details });
    if (error instanceof SyntaxError && error.status === 400) return response.status(400).json({ error: "Invalid JSON" });
    if (error?.code === "23503") return response.status(404).json({ error: "Player not found" });
    if (error?.code === "23514" || error?.code === "22007") return response.status(400).json({ error: "Invalid data" });
    console.error(error);
    response.status(500).json({ error: "Internal server error" });
  });
  return app;
}

function pageInteger(value, fallback, min, max) {
  if (value === undefined) return fallback;
  if (typeof value !== "string" || !/^\d+$/.test(value)) throw new ValidationError({ query: "Expected integer query parameter" });
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < min || number > max) throw new ValidationError({ query: `Expected value from ${min} to ${max}` });
  return number;
}
