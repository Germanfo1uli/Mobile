import test from "node:test";
import assert from "node:assert/strict";
import { TARGET_TYPES, GAME_RULES, applyEvent, createRoundState, roundSnapshot, advanceRound, GameError } from "../src/game.js";

const player = { difficulty: 2 };
const settings = { gameSpeed: 1, maxInsects: 3, bonusIntervalSeconds: 15, roundDurationSeconds: 60 };

test("catalog contains only alternative-fashion targets", () => {
  assert.deepEqual(TARGET_TYPES.map((type) => type.id), ["alt_red", "alt_silver"]);
});

test("targets run mainly sideways rather than drifting vertically", () => {
  const state = createRoundState(player, settings, Date.now());
  assert.ok(state.targets.every((target) => Math.abs(target.vx) > Math.abs(target.vy) * 5));
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
  assert.equal(state.score, first.points - 5);
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
