import test from "node:test";
import assert from "node:assert/strict";
import { PVP_RANKS, pvpRules, pvpTier, ratingChange } from "../src/pvp.js";
import { advanceRound, createRoundState } from "../src/game.js";

test("ranked rules are identical for both players and never shorter than 40 seconds", () => {
  assert.equal(pvpRules(0).roundDurationSeconds, 60);
  assert.equal(pvpRules(10).roundDurationSeconds, 40);
  assert.ok(pvpRules(10).gameSpeed > pvpRules(0).gameSpeed);
  assert.equal(pvpRules(5).difficulty, 3);
  assert.equal(PVP_RANKS.length, 11);
});

test("victory and defeat change rank, while rank rewards cannot be claimed twice", () => {
  const win = ratingChange(90, "win", 0);
  assert.deepEqual({ points: win.nextPoints, tier: win.newTier, reward: win.rewardClues },
    { points: 100, tier: 1, reward: 1000 });
  assert.equal(ratingChange(100, "loss", 1).nextPoints, 95);
  assert.equal(ratingChange(95, "win", 1).rewardClues, 0);
  assert.equal(ratingChange(1000, "win", 10).nextPoints, 1000);
  assert.equal(pvpTier(1000), 10);
});

test("ranked level one gets a golden target every 20 seconds", () => {
  const now = Date.now();
  const settings = pvpRules(0);
  const state = createRoundState({ difficulty: 3 }, settings, now,
    { level: 1, goldEnabled: true, goldRate: { rublesPerGram: 10000 } });
  assert.equal(state.level, 1);
  assert.equal(state.targets.some((target) => target.type === "alt_gold"), false);
  advanceRound(state, now + 20_000);
  assert.equal(state.targets.filter((target) => target.type === "alt_gold").length, 1);
});
