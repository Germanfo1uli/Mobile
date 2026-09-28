import { randomUUID, randomInt } from "node:crypto";
import { ValidationError, requireUuid } from "./validation.js";
import { findTheurgy } from "./theurgies.js";

// Coordinates are fractions of the playfield, not device-specific pixels.
export const TARGET_TYPES = [
  { id: "alt_red", title: "Беглянка", points: 10, speed: 0.12, radius: 0.10 },
  { id: "alt_silver", title: "Тень", points: 20, speed: 0.16, radius: 0.085 },
];

export const GAME_RULES = {
  missPenaltyByDifficulty: [10, 12, 15, 18, 20],
  bonusLifetimeSeconds: 5,
  tiltDurationSeconds: 10,
  freezeDurationSeconds: 8,
  multiplierDurationSeconds: 12,
  theurgyCutsceneMilliseconds: 4_000,
  tiltSpeed: 0.35,
  soundCue: "alt_slide",
  goldenIntervalSeconds: 20,
  goldenLifetimeSeconds: 10,
  levelTwoUnlockScore: 6000,
};

export const DIFFICULTY_PRESETS = {
  1: { gameSpeed: 0.7, maxInsects: 4, bonusIntervalSeconds: 10, roundDurationSeconds: 45 },
  2: { gameSpeed: 0.9, maxInsects: 5, bonusIntervalSeconds: 12, roundDurationSeconds: 50 },
  3: { gameSpeed: 1.1, maxInsects: 7, bonusIntervalSeconds: 15, roundDurationSeconds: 60 },
  4: { gameSpeed: 1.4, maxInsects: 9, bonusIntervalSeconds: 18, roundDurationSeconds: 75 },
  5: { gameSpeed: 1.7, maxInsects: 12, bonusIntervalSeconds: 22, roundDurationSeconds: 90 },
};

export class GameError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const random = () => randomInt(1_000_000) / 1_000_000;

function spawnTarget(state, now) {
  const type = TARGET_TYPES[randomInt(TARGET_TYPES.length)];
  const speed = type.speed * state.settings.gameSpeed;
  const quadrant = randomInt(4);
  const angle = Math.PI / 4 + quadrant * Math.PI / 2 + (random() - 0.5) * 0.42;
  return {
    id: randomUUID(), type: type.id, points: type.points * state.difficulty,
    radius: type.radius, x: type.radius + random() * (1 - 2 * type.radius),
    y: type.radius + random() * (1 - 2 * type.radius),
    vx: Math.cos(angle) * speed, vy: Math.sin(angle) * speed,
    positionedAt: now, expiresAt: now + 12_000,
  };
}

function spawnGoldenTarget(state, now) {
  const radius = 0.105;
  const speed = 0.19 * state.settings.gameSpeed;
  const angle = Math.PI / 4 + randomInt(4) * Math.PI / 2 + (random() - 0.5) * 0.35;
  return {
    id: randomUUID(), type: "alt_gold", title: "Золотая альтушка",
    points: Math.max(100, Math.round(state.goldRate.rublesPerGram / 10)),
    goldRate: state.goldRate.rublesPerGram, radius,
    x: radius + random() * (1 - 2 * radius), y: radius + random() * (1 - 2 * radius),
    vx: Math.cos(angle) * speed, vy: Math.sin(angle) * speed,
    positionedAt: now, expiresAt: now + GAME_RULES.goldenLifetimeSeconds * 1000,
  };
}

function reflectedPosition(value, radius) {
  const span = 1 - 2 * radius;
  const phase = ((value - radius) % (2 * span) + 2 * span) % (2 * span);
  return radius + (phase <= span ? phase : 2 * span - phase);
}

function reflectedVelocity(value, radius, velocity) {
  const span = 1 - 2 * radius;
  const phase = ((value - radius) % (2 * span) + 2 * span) % (2 * span);
  return phase <= span ? velocity : -velocity;
}

function positionAt(target, now, state) {
  const effectEnd = state.tiltUntil ?? 0;
  const from = target.positionedAt;
  const tiltSeconds = Math.max(0, Math.min(now, effectEnd) - from) / 1000;
  const normalStart = Math.max(from, effectEnd);
  const boostEnd = state.speedBoostUntil ?? 0;
  const boostedSeconds = Math.max(0, Math.min(now, boostEnd) - normalStart) / 1000;
  const normalSeconds = Math.max(0, now - Math.max(normalStart, boostEnd)) / 1000;
  const speedBoost = state.speedBoostValue ?? 1;
  const tilt = state.tilt ?? { x: 0, y: 0 };
  // Gravity pulls targets into corners; after the effect expires normal movement resumes.
  const tiltedX = Math.min(1 - target.radius, Math.max(target.radius, target.x + tilt.x * GAME_RULES.tiltSpeed * tiltSeconds));
  const tiltedY = Math.min(1 - target.radius, Math.max(target.radius, target.y + tilt.y * GAME_RULES.tiltSpeed * tiltSeconds));
  const unfoldedX = tiltedX + target.vx * (boostedSeconds * speedBoost + normalSeconds);
  const unfoldedY = tiltedY + target.vy * (boostedSeconds * speedBoost + normalSeconds);
  return {
    x: reflectedPosition(unfoldedX, target.radius),
    y: reflectedPosition(unfoldedY, target.radius),
    vx: reflectedVelocity(unfoldedX, target.radius, target.vx),
    vy: reflectedVelocity(unfoldedY, target.radius, target.vy),
  };
}

export function createRoundState(player, settings, now, options = {}) {
  const level = options.level ?? 1;
  const selectedTheurgy = findTheurgy(options.selectedTheurgy)?.id ?? "gravity";
  const state = {
    version: 2, level, difficulty: player.difficulty, settings,
    startedAt: now, endsAt: now + settings.roundDurationSeconds * 1000,
    score: 0, hits: 0, misses: 0, bonusesCollected: 0,
    nextBonusAt: now + settings.bonusIntervalSeconds * 1000,
    bonus: null, selectedTheurgy, tiltUntil: 0, freezeUntil: 0, multiplierUntil: 0,
    multiplierValue: 1, shieldUntil: 0,
    speedBoostUntil: 0, speedBoostValue: 1,
    penaltyMultiplierUntil: 0, penaltyMultiplierValue: 1,
    radiusMultiplierUntil: 0, radiusMultiplierValue: 1,
    theurgyUntil: 0, tilt: { x: 0, y: 0 }, targets: [],
    goldRate: level === 2 ? options.goldRate : null,
    nextGoldenAt: level === 2 ? now + GAME_RULES.goldenIntervalSeconds * 1000 : null,
  };
  for (let i = 0; i < settings.maxInsects; i++) state.targets.push(spawnTarget(state, now));
  return state;
}

export function advanceRound(state, now) {
  if (now >= state.endsAt) return false;
  if (now < state.theurgyUntil) return true;
  state.targets = state.targets.filter((target) => target.expiresAt > now);
  while (state.targets.filter((target) => target.type !== "alt_gold").length < state.settings.maxInsects) {
    state.targets.push(spawnTarget(state, now));
  }
  if (state.level === 2 && state.goldRate && now >= state.nextGoldenAt) {
    const interval = GAME_RULES.goldenIntervalSeconds * 1000;
    const lastScheduledAt = state.nextGoldenAt + Math.floor((now - state.nextGoldenAt) / interval) * interval;
    state.nextGoldenAt = lastScheduledAt + interval;
    if (lastScheduledAt + GAME_RULES.goldenLifetimeSeconds * 1000 > now && !state.targets.some((target) => target.type === "alt_gold")) {
      state.targets.push(spawnGoldenTarget(state, lastScheduledAt));
    }
  }
  if (state.bonus && state.bonus.expiresAt <= now) state.bonus = null;
  if (now >= state.nextBonusAt) {
    const interval = state.settings.bonusIntervalSeconds * 1000;
    const lastScheduledAt = state.nextBonusAt + Math.floor((now - state.nextBonusAt) / interval) * interval;
    state.nextBonusAt = lastScheduledAt + interval;
    if (lastScheduledAt + GAME_RULES.bonusLifetimeSeconds * 1000 > now) {
      const selected = findTheurgy(state.selectedTheurgy) ?? findTheurgy("gravity");
      state.bonus = {
        id: randomUUID(), type: "theurgy", theurgyId: selected.id, title: selected.title,
        x: 0.14 + random() * 0.72, y: 0.14 + random() * 0.72,
        expiresAt: lastScheduledAt + GAME_RULES.bonusLifetimeSeconds * 1000,
      };
    }
  }
  return true;
}

export function roundSnapshot(round, now) {
  const state = round.state;
  return {
    id: round.id, playerId: round.player_id, status: round.status, level: state.level ?? 1,
    serverTime: new Date(now).toISOString(),
    startedAt: new Date(state.startedAt).toISOString(),
    endsAt: new Date(state.endsAt).toISOString(),
    remainingMilliseconds: round.status === "active" ? Math.max(0, state.endsAt - Math.max(now, state.theurgyUntil ?? 0)) : 0,
    theurgyRemainingMilliseconds: round.status === "active" ? Math.max(0, (state.theurgyUntil ?? 0) - now) : 0,
    score: state.score, hits: state.hits, misses: state.misses,
    missPenalty: GAME_RULES.missPenaltyByDifficulty[state.difficulty - 1],
    difficulty: state.difficulty, settings: state.settings,
    bonusesCollected: state.bonusesCollected,
    selectedTheurgy: state.selectedTheurgy ?? "gravity",
    targets: round.status === "active" ? state.targets.map((target) => {
      const position = positionAt(target, now, state);
      return {
        id: target.id, type: target.type, points: target.points, radius: effectiveRadius(state, target, now),
        goldRate: target.goldRate ?? null,
        x: position.x, y: position.y,
        velocity: state.tiltUntil > now && (state.theurgyUntil ?? 0) <= now
          ? { x: state.tilt.x * GAME_RULES.tiltSpeed, y: state.tilt.y * GAME_RULES.tiltSpeed }
          : { x: position.vx * activeSpeedBoost(state, now), y: position.vy * activeSpeedBoost(state, now) },
        expiresAt: new Date(target.expiresAt).toISOString(),
      };
    }) : [],
    bonus: round.status === "active" && state.bonus ? {
      ...state.bonus, expiresAt: new Date(state.bonus.expiresAt).toISOString(),
    } : null,
    effect: activeEffect(state, now),
  };
}

export function validateEvent(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new ValidationError({ body: "Must be an object" });
  requireUuid(body.eventId, "eventId");
  if (!["tap", "collect_bonus", "tilt"].includes(body.type)) throw new ValidationError({ type: "Must be tap, collect_bonus or tilt" });
  if (body.type === "collect_bonus") {
    return { eventId: body.eventId, type: body.type, bonusId: requireUuid(body.bonusId, "bonusId") };
  }
  const min = body.type === "tap" ? 0 : -1;
  for (const key of ["x", "y"]) {
    if (typeof body[key] !== "number" || !Number.isFinite(body[key]) || body[key] < min || body[key] > 1) {
      throw new ValidationError({ [key]: `Must be a number from ${min} to 1` });
    }
  }
  return { eventId: body.eventId, type: body.type, x: body.x, y: body.y };
}

export function applyEvent(state, event, now) {
  if (now < (state.theurgyUntil ?? 0)) throw new GameError(409, "Theurgy cutscene is playing");
  if (event.type === "tap") {
    const index = state.targets.findIndex((target) => {
      const position = positionAt(target, now, state);
      return Math.hypot(event.x - position.x, event.y - position.y) <= effectiveRadius(state, target, now);
    });
    if (index < 0) {
      const basePenalty = GAME_RULES.missPenaltyByDifficulty[state.difficulty - 1];
      const penalty = (state.shieldUntil ?? 0) > now ? 0 : basePenalty * activePenaltyMultiplier(state, now);
      state.misses++;
      state.score = Math.max(0, state.score - penalty);
      return { type: "miss", penalty };
    }
    const [target] = state.targets.splice(index, 1);
    const multiplier = (state.multiplierUntil ?? 0) > now ? (state.multiplierValue ?? 2) : 1;
    const awardedPoints = target.points * multiplier;
    state.hits++;
    state.score += awardedPoints;
    if (target.type !== "alt_gold") state.targets.push(spawnTarget(state, now));
    return { type: "hit", targetId: target.id, awardedPoints };
  }
  if (event.type === "collect_bonus") {
    if (!state.bonus || state.bonus.id !== event.bonusId) throw new GameError(409, "Bonus is no longer available");
    reanchorTargets(state, now);
    state.bonus = null;
    state.bonusesCollected++;
    const cutscene = GAME_RULES.theurgyCutsceneMilliseconds;
    state.endsAt += cutscene;
    state.nextBonusAt += cutscene;
    state.theurgyUntil = now + cutscene;
    for (const target of state.targets) {
      target.positionedAt = state.theurgyUntil;
      target.expiresAt += cutscene;
    }
    const selected = findTheurgy(state.selectedTheurgy) ?? findTheurgy("gravity");
    state.tiltUntil = 0;
    state.freezeUntil = 0;
    state.multiplierUntil = 0;
    state.multiplierValue = 1;
    state.shieldUntil = 0;
    state.speedBoostUntil = 0;
    state.speedBoostValue = 1;
    state.penaltyMultiplierUntil = 0;
    state.penaltyMultiplierValue = 1;
    state.radiusMultiplierUntil = 0;
    state.radiusMultiplierValue = 1;
    state.armageddonResult = null;
    if (selected.effectType === "tilt") {
      state.tiltUntil = Math.min(state.theurgyUntil + selected.durationSeconds * 1000, state.endsAt);
    } else if (selected.effectType === "freeze") {
      state.freezeUntil = Math.min(state.theurgyUntil + selected.durationSeconds * 1000, state.endsAt);
      for (const target of state.targets) {
        target.positionedAt = state.freezeUntil;
        target.expiresAt += selected.durationSeconds * 1000;
      }
    } else if (selected.effectType === "multiplier") {
      state.multiplierUntil = Math.min(state.theurgyUntil + selected.durationSeconds * 1000, state.endsAt);
      state.multiplierValue = selected.multiplier ?? 2;
      if (selected.id === "scarlet") {
        state.speedBoostUntil = state.multiplierUntil;
        state.speedBoostValue = 1.5;
        state.penaltyMultiplierUntil = state.multiplierUntil;
        state.penaltyMultiplierValue = 2;
      } else if (selected.id === "moonfall") {
        state.penaltyMultiplierUntil = state.multiplierUntil;
        state.penaltyMultiplierValue = 3;
        state.radiusMultiplierUntil = state.multiplierUntil;
        state.radiusMultiplierValue = 0.68;
      }
    } else if (selected.effectType === "shield") {
      state.shieldUntil = Math.min(state.theurgyUntil + selected.durationSeconds * 1000, state.endsAt);
      state.speedBoostUntil = state.shieldUntil;
      state.speedBoostValue = 2;
    } else if (selected.effectType === "armageddon") {
      const multiplier = selected.multiplier ?? 5;
      const destroyed = state.targets.length;
      const awardedPoints = state.targets.reduce((sum, target) => sum + target.points * multiplier, 0);
      state.targets = [];
      state.hits += destroyed;
      state.score += awardedPoints;
      state.armageddonResult = { destroyed, awardedPoints };
    }
    state.tilt = { x: 0, y: 0 };
    return { type: "bonus_collected", theurgyId: selected.id, effectType: selected.effectType,
      awardedPoints: state.armageddonResult?.awardedPoints ?? 0, soundCue: GAME_RULES.soundCue };
  }
  if (state.tiltUntil <= now) throw new GameError(409, "Tilt bonus is not active");
  reanchorTargets(state, now);
  state.tilt = { x: event.x, y: event.y };
  return { type: "tilt_updated" };
}

function activeSpeedBoost(state, now) {
  return (state.speedBoostUntil ?? 0) > now ? (state.speedBoostValue ?? 1) : 1;
}

function activePenaltyMultiplier(state, now) {
  return (state.penaltyMultiplierUntil ?? 0) > now ? (state.penaltyMultiplierValue ?? 1) : 1;
}

function effectiveRadius(state, target, now) {
  const multiplier = (state.radiusMultiplierUntil ?? 0) > now ? (state.radiusMultiplierValue ?? 1) : 1;
  return target.radius * multiplier;
}

function activeEffect(state, now) {
  if ((state.theurgyUntil ?? 0) > now) return null;
  const selected = findTheurgy(state.selectedTheurgy) ?? findTheurgy("gravity");
  if ((state.tiltUntil ?? 0) > now) {
    return {
      type: "tilt", title: selected.title, until: new Date(state.tiltUntil).toISOString(),
      direction: state.tilt, speed: GAME_RULES.tiltSpeed,
    };
  }
  if ((state.freezeUntil ?? 0) > now) {
    return { type: "freeze", title: selected.title, until: new Date(state.freezeUntil).toISOString() };
  }
  if ((state.multiplierUntil ?? 0) > now) {
    return { type: "multiplier", title: selected.title, until: new Date(state.multiplierUntil).toISOString(), multiplier: state.multiplierValue ?? 2 };
  }
  if ((state.shieldUntil ?? 0) > now) {
    return { type: "shield", title: selected.title, until: new Date(state.shieldUntil).toISOString() };
  }
  return null;
}

function reanchorTargets(state, now) {
  for (const target of state.targets) {
    Object.assign(target, positionAt(target, now, state), { positionedAt: now });
  }
}
