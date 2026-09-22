import assert from "node:assert/strict";
import pg from "pg";

const api = "http://127.0.0.1:8080/api";
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL ?? "postgres://bugs:bugs@127.0.0.1:5432/bugs" });
const call = async (path, method = "GET", body) => {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${JSON.stringify(json)}`);
  return json;
};

let playerId;
try {
  const created = await call("/players", "POST", {
    fullName: "Проверка Рекордов", gender: "Не указан", course: 2, difficulty: 1,
    birthDate: "2000-01-01", zodiac: "Козерог",
  });
  playerId = created.player.id;
  const settings = await call(`/players/${playerId}/settings`, "PUT", {
    gameSpeed: 1.5, maxInsects: 3, bonusIntervalSeconds: 10,
    roundDurationSeconds: 30, difficulty: 4,
  });
  assert.equal(settings.settings.difficulty, 4);
  assert.equal((await call(`/players/${playerId}/settings`)).settings.difficulty, 4);

  const first = (await call("/rounds", "POST", { playerId })).round;
  assert.equal(first.difficulty, 4);
  await call(`/rounds/${first.id}/finish`, "POST", {});

  const second = (await call("/rounds", "POST", { playerId })).round;
  const target = second.targets[0];
  const event = (await call(`/rounds/${second.id}/events`, "POST", {
    eventId: crypto.randomUUID(), type: "tap", x: target.x, y: target.y,
  })).event;
  assert.equal(event.type, "hit");
  const finished = (await call(`/rounds/${second.id}/finish`, "POST", {})).round;
  assert.ok(finished.score > 0);
  const records = (await call("/records?limit=100")).records.filter((record) => record.player_id === playerId);
  assert.equal(records.length, 1);
  assert.equal(records[0].score, finished.score);
  console.log(`LIVE OK: difficulty=4, two rounds, leaderboard best=${finished.score} once`);
} finally {
  if (playerId) await pool.query("DELETE FROM players WHERE id = $1", [playerId]);
  await pool.end();
}
