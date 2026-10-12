import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

const base = process.env.ALTHUNT_TEST_API ?? "http://127.0.0.1:8080/api";
const createdPlayers = [];

async function call(path, method = "GET", body) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = response.status === 204 ? {} : await response.json();
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${JSON.stringify(value)}`);
  return value;
}

async function createPlayer(label) {
  const value = await call("/players", "POST", {
    fullName: `Тест ${label} ${randomUUID().slice(0, 8)}`,
    gender: "Мужской", course: 2, difficulty: 2,
    birthDate: "2004-05-17", zodiac: "Телец",
  });
  createdPlayers.push(value.player.id);
  return value.player.id;
}

try {
  const first = await createPlayer("Охотник");
  const second = await createPlayer("Соперник");
  const searches = await Promise.all([
    call("/matchmaking/search", "POST", { playerId: first }),
    call("/matchmaking/search", "POST", { playerId: second }),
  ]);
  assert.equal(searches[0].match.code, searches[1].match.code);
  const code = searches[0].match.code;
  const paired = (await call(`/matches/${code}`)).match;
  assert.equal(paired.status, "running");
  assert.equal(paired.players.length, 2);
  assert.equal(paired.winner, null);
  assert.equal(paired.rules.roundDurationSeconds, 60);
  assert.equal(paired.rules.difficulty, 3);

  const repeated = (await call("/matchmaking/search", "POST", { playerId: first })).match;
  assert.equal(repeated.code, code);

  const [firstRound, secondRound] = await Promise.all([
    call(`/matches/${code}/round`, "POST", { playerId: first }),
    call(`/matches/${code}/round`, "POST", { playerId: second }),
  ]);
  assert.notEqual(firstRound.round.id, secondRound.round.id);
  assert.deepEqual(firstRound.round.settings, secondRound.round.settings);
  assert.equal(firstRound.round.difficulty, 3);
  assert.equal(firstRound.round.settings.roundDurationSeconds, 60);
  const target = firstRound.round.targets[0];
  await call(`/rounds/${firstRound.round.id}/events`, "POST", {
    eventId: randomUUID(), type: "tap", targetId: target.id, x: target.x, y: target.y,
  });
  await call(`/rounds/${firstRound.round.id}/finish`, "POST", {});
  const half = (await call(`/matches/${code}`)).match;
  assert.equal(half.status, "running");
  assert.equal(half.winner, null);
  assert.equal(half.players.find((item) => item.playerId === first).submitted, true);

  await call(`/rounds/${secondRound.round.id}/finish`, "POST", {});
  const final = (await call(`/matches/${code}`)).match;
  assert.equal(final.status, "finished");
  assert.equal(final.results.length, 2);
  assert.equal(final.winner.playerId, first);
  assert.ok(final.winner.score > final.results[1].score);
  assert.equal(final.players.find((item) => item.playerId === first).ratingDelta, 10);
  const resumedAfterFinish = await call(`/matches/${code}/round`, "POST", { playerId: second });
  assert.equal(resumedAfterFinish.round.id, secondRound.round.id);
  assert.equal(resumedAfterFinish.round.status, "finished");
  const repeatedResult = await call(`/matches/${code}/results`, "POST", { playerId: second, roundId: secondRound.round.id });
  assert.equal(repeatedResult.match.status, "finished");
  const firstRating = (await call(`/pvp/players/${first}`)).rating;
  const secondRating = (await call(`/pvp/players/${second}`)).rating;
  assert.equal(firstRating.points, 10);
  assert.equal(secondRating.points, 0);
  assert.equal(firstRating.wins, 1);
  assert.equal(secondRating.losses, 1);
  console.log(`Local match verified: ${code}, ${final.winner.score}:${final.results[1].score}`);

  const solo = await createPlayer("Одиночка");
  const searching = (await call("/matchmaking/search", "POST", { playerId: solo })).match;
  assert.equal(searching.status, "waiting");
  await delay(20_500);
  const expired = (await call(`/matches/${searching.code}`)).match;
  assert.equal(expired.status, "expired");
  const next = (await call("/matchmaking/search", "POST", { playerId: solo })).match;
  assert.equal(next.status, "waiting");
  assert.notEqual(next.code, searching.code);
  assert.equal((await call("/matchmaking/cancel", "POST", { playerId: solo })).cancelled, true);
  console.log("20-second search timeout and retry verified");
} finally {
  for (const id of createdPlayers) {
    try { await call(`/players/${id}`, "DELETE"); }
    catch (error) { console.error(`Could not remove test player ${id}:`, error.message); }
  }
}
