import test from "node:test";
import assert from "node:assert/strict";
import { TARGET_TYPES, GAME_RULES, DIFFICULTY_PRESETS, applyEvent, createRoundState, roundSnapshot, advanceRound, GameError } from "../src/game.js";

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
