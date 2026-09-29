import test from "node:test";
import assert from "node:assert/strict";
import { TARGET_TYPES, GAME_RULES, DIFFICULTY_PRESETS, applyEvent, createRoundState, roundSnapshot, advanceRound, GameError, validateEvent } from "../src/game.js";
import { parseGoldXml } from "../src/gold.js";

const player = { difficulty: 2 };
const settings = { gameSpeed: 1, maxInsects: 3, bonusIntervalSeconds: 15, roundDurationSeconds: 60 };

test("catalog contains only alternative-fashion targets", () => {
  assert.deepEqual(TARGET_TYPES.map((type) => type.id), ["alt_red", "alt_silver"]);
});

test("targets move diagonally across both axes", () => {
  const state = createRoundState(player, settings, Date.now());
  assert.ok(state.targets.every((target) => Math.abs(target.vx) > 0 && Math.abs(target.vy) > 0));
  assert.ok(state.targets.every((target) => Math.abs(target.vy) >= Math.hypot(target.vx, target.vy) * 0.45));
});

test("server calculates hits, misses and a nonnegative score", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now);
  const first = state.targets[0];
  const hit = applyEvent(state, { type: "tap", x: first.x, y: first.y }, now);
  assert.equal(hit.type, "hit");
  assert.equal(state.score, first.points);
  assert.equal(state.hits, 1);
  assert.equal(state.targets.length, settings.maxInsects);

  const miss = applyEvent(state, { type: "tap", x: 0, y: 0 }, now);
  assert.equal(miss.type, "miss");
  assert.equal(miss.penalty, 12);
  assert.equal(state.score, first.points - 12);
  assert.equal(state.misses, 1);
  for (let index = 0; index < 20; index++) applyEvent(state, { type: "tap", x: 0, y: 0 }, now);
  assert.equal(state.score, 0);

  const round = { id: "round-id", player_id: "player-id", status: "active", state };
  const snapshot = roundSnapshot(round, now);
  assert.equal(snapshot.targets.length, settings.maxInsects);
  assert.equal(snapshot.score, state.score);
});

test("target id preserves a visible hit across cloud latency", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now);
  const target = state.targets[0];
  const event = validateEvent({
    eventId: "3f010a84-c46d-4d2a-bec7-ab068cf35b3c",
    type: "tap",
    targetId: target.id,
    x: target.x,
    y: target.y,
  });
  assert.equal(event.targetId, target.id);
  const hit = applyEvent(state, event, now + 4_000);
  assert.equal(hit.type, "hit");
  assert.equal(hit.targetId, target.id);
  assert.equal(state.hits, 1);
});

test("theurgy cutscene pauses timer and target motion before freeze begins", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now);
  const originalDeadline = state.endsAt;
  const target = state.targets[0];
  state.bonus = { id: "bonus", expiresAt: now + 5_000 };
  assert.equal(applyEvent(state, { type: "collect_bonus", bonusId: "bonus" }, now).type, "bonus_collected");
  assert.equal(state.endsAt, originalDeadline + GAME_RULES.theurgyCutsceneMilliseconds);
  assert.equal(state.bonusesCollected, 1);
  const round = { id: "round-id", player_id: "player-id", status: "active", state };
  const first = roundSnapshot(round, now);
  const during = roundSnapshot(round, now + 2_000);
  assert.equal(during.remainingMilliseconds, first.remainingMilliseconds);
  assert.equal(during.targets.find((item) => item.id === target.id).x, first.targets.find((item) => item.id === target.id).x);
  assert.equal(advanceRound(state, now + 2_000), true);
  assert.throws(() => applyEvent(state, { type: "tap", x: 0.5, y: 0.5 }, now + 2_000), GameError);
  const after = roundSnapshot(round, now + GAME_RULES.theurgyCutsceneMilliseconds);
  assert.equal(after.theurgyRemainingMilliseconds, 0);
  assert.equal(after.effect.type, "tilt");
});

test("difficulty presets increase the pressure and target value", () => {
  for (let level = 2; level <= 5; level++) {
    const easier = DIFFICULTY_PRESETS[level - 1];
    const harder = DIFFICULTY_PRESETS[level];
    assert.ok(harder.gameSpeed > easier.gameSpeed);
    assert.ok(harder.maxInsects > easier.maxInsects);
    assert.ok(harder.bonusIntervalSeconds > easier.bonusIntervalSeconds);
    assert.ok(harder.roundDurationSeconds > easier.roundDurationSeconds);
    assert.ok(GAME_RULES.missPenaltyByDifficulty[level - 1] > GAME_RULES.missPenaltyByDifficulty[level - 2]);
  }
});

test("tilt input pulls targets toward the selected corner", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now);
  state.bonus = { id: "bonus", expiresAt: now + 5_000 };
  applyEvent(state, { type: "collect_bonus", bonusId: "bonus" }, now);
  const effectStarts = now + GAME_RULES.theurgyCutsceneMilliseconds;
  applyEvent(state, { type: "tilt", x: 0.8, y: -0.7 }, effectStarts);
  const first = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts);
  const moved = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts + 500);
  assert.ok(moved.targets[0].x > first.targets[0].x);
  assert.ok(moved.targets[0].y < first.targets[0].y);
  assert.ok(Math.abs(moved.targets[0].velocity.x - 0.28) < 0.0001);
  assert.ok(Math.abs(moved.targets[0].velocity.y + 0.245) < 0.0001);
});

test("theurgy bonus appears inside a random safe area", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now);
  advanceRound(state, state.nextBonusAt);
  assert.ok(state.bonus.x >= 0.14 && state.bonus.x <= 0.86);
  assert.ok(state.bonus.y >= 0.14 && state.bonus.y <= 0.86);
});

test("chrono theurgy freezes targets after the cutscene", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now, { selectedTheurgy: "chrono" });
  state.bonus = { id: "bonus", expiresAt: now + 5_000 };
  applyEvent(state, { type: "collect_bonus", bonusId: "bonus" }, now);
  const effectStarts = now + GAME_RULES.theurgyCutsceneMilliseconds;
  const first = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts);
  const later = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts + 2_000);
  assert.equal(later.effect.type, "freeze");
  assert.equal(later.targets[0].x, first.targets[0].x);
  assert.equal(later.targets[0].y, first.targets[0].y);
});

test("scarlet theurgy doubles captured target value", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now, { selectedTheurgy: "scarlet" });
  state.bonus = { id: "bonus", expiresAt: now + 5_000 };
  applyEvent(state, { type: "collect_bonus", bonusId: "bonus" }, now);
  const effectStarts = now + GAME_RULES.theurgyCutsceneMilliseconds;
  const target = state.targets[0];
  const hit = applyEvent(state, { type: "tap", x: target.x, y: target.y }, effectStarts);
  assert.equal(hit.awardedPoints, target.points * 2);
  assert.equal(state.score, target.points * 2);
  const snapshot = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts);
  assert.equal(snapshot.effect.type, "multiplier");
  assert.equal(applyEvent(state, { type: "tap", x: 0, y: 0 }, effectStarts).penalty, 24);
  assert.ok(Math.abs(snapshot.targets[0].velocity.x) > Math.abs(state.targets[0].vx));
});

test("mirror theurgy cancels miss penalties", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now, { selectedTheurgy: "mirror" });
  state.score = 100;
  state.bonus = { id: "bonus", expiresAt: now + 5_000 };
  applyEvent(state, { type: "collect_bonus", bonusId: "bonus" }, now);
  const effectStarts = now + GAME_RULES.theurgyCutsceneMilliseconds;
  const miss = applyEvent(state, { type: "tap", x: 0, y: 0 }, effectStarts);
  assert.equal(miss.penalty, 0);
  assert.equal(state.score, 100);
  const snapshot = roundSnapshot({ id: "round", player_id: "player", status: "active", state }, effectStarts);
  assert.ok(Math.abs(snapshot.targets[0].velocity.x) >= Math.abs(state.targets[0].vx) * 1.99);
});

test("moonfall triples points and armageddon clears the playfield", () => {
  const now = Date.now();
  const moonfall = createRoundState(player, settings, now, { selectedTheurgy: "moonfall" });
  moonfall.bonus = { id: "moon", expiresAt: now + 5_000 };
  applyEvent(moonfall, { type: "collect_bonus", bonusId: "moon" }, now);
  const effectStarts = now + GAME_RULES.theurgyCutsceneMilliseconds;
  const target = moonfall.targets[0];
  assert.equal(applyEvent(moonfall, { type: "tap", x: target.x, y: target.y }, effectStarts).awardedPoints, target.points * 3);
  const moonSnapshot = roundSnapshot({ id: "round", player_id: "player", status: "active", state: moonfall }, effectStarts);
  assert.ok(moonSnapshot.targets[0].radius < moonfall.targets[0].radius);
  assert.equal(applyEvent(moonfall, { type: "tap", x: 0, y: 0 }, effectStarts).penalty, 36);

  const armageddon = createRoundState(player, settings, now, { selectedTheurgy: "armageddon" });
  const expected = armageddon.targets.reduce((sum, item) => sum + item.points * 5, 0);
  const count = armageddon.targets.length;
  armageddon.bonus = { id: "last", expiresAt: now + 5_000 };
  const result = applyEvent(armageddon, { type: "collect_bonus", bonusId: "last" }, now);
  assert.equal(result.awardedPoints, expected);
  assert.equal(armageddon.score, expected);
  assert.equal(armageddon.hits, count);
  assert.equal(armageddon.targets.length, 0);
});

test("level two spawns a golden target every twenty seconds with CBR-proportional points", () => {
  const now = Date.now();
  const state = createRoundState(player, settings, now, {
    level: 2,
    goldRate: { rublesPerGram: 12_345.67, date: "27.09.2026" },
  });
  advanceRound(state, now + GAME_RULES.goldenIntervalSeconds * 1000);
  const golden = state.targets.find((target) => target.type === "alt_gold");
  assert.ok(golden);
  assert.equal(golden.points, 1235);
  assert.equal(golden.goldRate, 12_345.67);
});

test("gold XML parser selects the latest Bank of Russia gold quotation", () => {
  const result = parseGoldXml(`<?xml version="1.0"?><Metall>
    <Record Date="25.09.2026" Code="1"><Buy>10100,50</Buy><Sell>10200,00</Sell></Record>
    <Record Date="26.09.2026" Code="2"><Buy>120,00</Buy><Sell>121,00</Sell></Record>
    <Record Date="26.09.2026" Code="1"><Buy>10321,45</Buy><Sell>10400,00</Sell></Record>
  </Metall>`);
  assert.deepEqual(result, { rublesPerGram: 10_321.45, date: "26.09.2026" });
});
