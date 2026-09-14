const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export class ValidationError extends Error {
  constructor(details) {
    super("Validation failed");
    this.name = "ValidationError";
    this.details = details;
  }
}

function integerInRange(value, min, max, field, errors) {
  if (!Number.isInteger(value) || value < min || value > max) {
    errors[field] = `Must be an integer from ${min} to ${max}`;
  }
}

export function requireUuid(value, field = "id") {
  if (typeof value !== "string" || !UUID.test(value)) {
    throw new ValidationError({ [field]: "Must be a UUID" });
  }
  return value;
}

export function validatePlayer(body = {}) {
  const errors = {};
  const fullName = typeof body.fullName === "string" ? body.fullName.trim() : "";
  if (fullName.length < 2 || fullName.length > 200) errors.fullName = "Length must be 2-200 characters";
  if (typeof body.gender !== "string" || body.gender.trim().length === 0 || body.gender.length > 20) {
    errors.gender = "Must be a non-empty string up to 20 characters";
  }
  integerInRange(body.course, 1, 6, "course", errors);
  integerInRange(body.difficulty, 1, 5, "difficulty", errors);
  if (typeof body.birthDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(body.birthDate)) {
    errors.birthDate = "Must use YYYY-MM-DD format";
  }
  if (typeof body.zodiac !== "string" || body.zodiac.trim().length === 0 || body.zodiac.length > 20) {
    errors.zodiac = "Must be a non-empty string up to 20 characters";
  }
  if (Object.keys(errors).length) throw new ValidationError(errors);
  return { fullName, gender: body.gender.trim(), course: body.course, difficulty: body.difficulty, birthDate: body.birthDate, zodiac: body.zodiac.trim() };
}

export function validateSettings(body = {}) {
  const errors = {};
  const gameSpeed = Number(body.gameSpeed);
  if (!Number.isFinite(gameSpeed) || gameSpeed < 0.5 || gameSpeed > 2) errors.gameSpeed = "Must be from 0.5 to 2.0";
  integerInRange(body.maxInsects, 3, 15, "maxInsects", errors);
  integerInRange(body.bonusIntervalSeconds, 5, 30, "bonusIntervalSeconds", errors);
  integerInRange(body.roundDurationSeconds, 30, 180, "roundDurationSeconds", errors);
  if (Object.keys(errors).length) throw new ValidationError(errors);
  return { gameSpeed, maxInsects: body.maxInsects, bonusIntervalSeconds: body.bonusIntervalSeconds, roundDurationSeconds: body.roundDurationSeconds };
}

export function validateResult(body = {}) {
  const errors = {};
  requireUuid(body.playerId, "playerId");
  integerInRange(body.score, 0, 1_000_000_000, "score", errors);
  integerInRange(body.hits ?? 0, 0, 1_000_000_000, "hits", errors);
  integerInRange(body.misses ?? 0, 0, 1_000_000_000, "misses", errors);
  integerInRange(body.difficulty, 1, 5, "difficulty", errors);
  if (Object.keys(errors).length) throw new ValidationError(errors);
  return { playerId: body.playerId, score: body.score, hits: body.hits ?? 0, misses: body.misses ?? 0, difficulty: body.difficulty };
}
