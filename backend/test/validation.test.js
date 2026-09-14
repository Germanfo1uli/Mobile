import test from "node:test";
import assert from "node:assert/strict";
import { ValidationError, validatePlayer, validateResult, validateSettings } from "../src/validation.js";

test("valid player is normalized", () => {
  assert.deepEqual(validatePlayer({
    fullName: "  Иванов Иван  ", gender: "Мужской", course: 2, difficulty: 3,
    birthDate: "2004-05-17", zodiac: "Телец",
  }), {
    fullName: "Иванов Иван", gender: "Мужской", course: 2, difficulty: 3,
    birthDate: "2004-05-17", zodiac: "Телец",
  });
});

test("invalid settings are rejected", () => {
  assert.throws(
    () => validateSettings({ gameSpeed: 4, maxInsects: 1, bonusIntervalSeconds: 2, roundDurationSeconds: 10 }),
    ValidationError,
  );
});

test("result accepts zero score", () => {
  const result = validateResult({
    playerId: "123e4567-e89b-42d3-a456-426614174000",
    score: 0,
    difficulty: 1,
  });
  assert.equal(result.hits, 0);
  assert.equal(result.misses, 0);
});
