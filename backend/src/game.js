import { randomUUID, randomInt } from "node:crypto";
import { ValidationError, requireUuid } from "./validation.js";

// Coordinates are fractions of the playfield, not device-specific pixels.
export const TARGET_TYPES = [
  { id: "alt_red", title: "Беглянка", points: 10, speed: 0.12, radius: 0.10 },
  { id: "alt_silver", title: "Тень", points: 20, speed: 0.16, radius: 0.085 },
];

export const GAME_RULES = {
  missPenalty: 5,
  bonusLifetimeSeconds: 5,
  tiltDurationSeconds: 10,
  theurgyCutsceneMilliseconds: 4_000,
  tiltSpeed: 0.35,
  soundCue: "target_spotted",
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
  const speed = type.speed * state.settings.gameSpeed * (1 + (state.difficulty - 1) * 0.2);
  const direction = random() < 0.5 ? -1 : 1;
  return {
    id: randomUUID(), type: type.id, points: type.points * state.difficulty,
    radius: type.radius, x: type.radius + random() * (1 - 2 * type.radius),
    y: type.radius + random() * (1 - 2 * type.radius),
    vx: direction * speed, vy: (random() - 0.5) * speed * 0.24,
    positionedAt: now, expiresAt: now + 12_000,
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
  const normalSeconds = Math.max(0, now - Math.max(from, effectEnd)) / 1000;
  const tilt = state.tilt ?? { x: 0, y: 0 };
  // Gravity pulls targets into corners; after the effect expires normal movement resumes.
  const tiltedX = Math.min(1 - target.radius, Math.max(target.radius, target.x + tilt.x * GAME_RULES.tiltSpeed * tiltSeconds));
  const tiltedY = Math.min(1 - target.radius, Math.max(target.radius, target.y + tilt.y * GAME_RULES.tiltSpeed * tiltSeconds));
  const unfoldedX = tiltedX + target.vx * normalSeconds;
  const unfoldedY = tiltedY + target.vy * normalSeconds;
  return {
    x: reflectedPosition(unfoldedX, target.radius),
    y: reflectedPosition(unfoldedY, target.radius),
    vx: reflectedVelocity(unfoldedX, target.radius, target.vx),
    vy: reflectedVelocity(unfoldedY, target.radius, target.vy),
  };
}

export function createRoundState(player, settings, now) {
  const state = {
    version: 1, difficulty: player.difficulty, settings,
    startedAt: now, endsAt: now + settings.roundDurationSeconds * 1000,
    score: 0, hits: 0, misses: 0, bonusesCollected: 0,
    nextBonusAt: now + settings.bonusIntervalSeconds * 1000,
    bonus: null, tiltUntil: 0, theurgyUntil: 0, tilt: { x: 0, y: 0 }, targets: [],
  };
  for (let i = 0; i < settings.maxInsects; i++) state.targets.push(spawnTarget(state, now));
  return state;
}

export function advanceRound(state, now) {
  if (now >= state.endsAt) return false;
  if (now < state.theurgyUntil) return true;
  state.targets = state.targets.filter((target) => target.expiresAt > now);
  while (state.targets.length < state.settings.maxInsects) state.targets.push(spawnTarget(state, now));
  if (state.bonus && state.bonus.expiresAt <= now) state.bonus = null;
  if (now >= state.nextBonusAt) {
    const interval = state.settings.bonusIntervalSeconds * 1000;
    const lastScheduledAt = state.nextBonusAt + Math.floor((now - state.nextBonusAt) / interval) * interval;
    state.nextBonusAt = lastScheduledAt + interval;
    if (lastScheduledAt + GAME_RULES.bonusLifetimeSeconds * 1000 > now) {
      state.bonus = {
        id: randomUUID(), type: "theurgy", x: 0.5, y: 0.5,
        expiresAt: lastScheduledAt + GAME_RULES.bonusLifetimeSeconds * 1000,
      };
    }
  }
  return true;
}

export function roundSnapshot(round, now) {
  const state = round.state;
  return {
    id: round.id, playerId: round.player_id, status: round.status,
    serverTime: new Date(now).toISOString(),
    startedAt: new Date(state.startedAt).toISOString(),
    endsAt: new Date(state.endsAt).toISOString(),
    remainingMilliseconds: round.status === "active" ? Math.max(0, state.endsAt - Math.max(now, state.theurgyUntil ?? 0)) : 0,
    theurgyRemainingMilliseconds: round.status === "active" ? Math.max(0, (state.theurgyUntil ?? 0) - now) : 0,
    score: state.score, hits: state.hits, misses: state.misses,
    difficulty: state.difficulty, settings: state.settings,
    bonusesCollected: state.bonusesCollected,
    targets: round.status === "active" ? state.targets.map((target) => {
      const position = positionAt(target, now, state);
      return {
        id: target.id, type: target.type, points: target.points, radius: target.radius,
        x: position.x, y: position.y,
        velocity: { x: position.vx, y: position.vy },
        expiresAt: new Date(target.expiresAt).toISOString(),
      };
    }) : [],
    bonus: round.status === "active" && state.bonus ? {
      ...state.bonus, expiresAt: new Date(state.bonus.expiresAt).toISOString(),
    } : null,
    effect: round.status === "active" && state.tiltUntil > now && (state.theurgyUntil ?? 0) <= now ? {
      type: "tilt", until: new Date(state.tiltUntil).toISOString(),
      direction: state.tilt, speed: GAME_RULES.tiltSpeed,
    } : null,
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
      return Math.hypot(event.x - position.x, event.y - position.y) <= target.radius;
    });
    if (index < 0) {
      state.misses++;
      state.score = Math.max(0, state.score - GAME_RULES.missPenalty);
      return { type: "miss", penalty: GAME_RULES.missPenalty };
    }
    const [target] = state.targets.splice(index, 1);
    state.hits++;
    state.score += target.points;
    state.targets.push(spawnTarget(state, now));
    return { type: "hit", targetId: target.id, awardedPoints: target.points };
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
    state.tiltUntil = Math.min(state.theurgyUntil + GAME_RULES.tiltDurationSeconds * 1000, state.endsAt);
    state.tilt = { x: 0, y: 0 };
    return { type: "bonus_collected", soundCue: GAME_RULES.soundCue };
  }
  if (state.tiltUntil <= now) throw new GameError(409, "Tilt bonus is not active");
  reanchorTargets(state, now);
  state.tilt = { x: event.x, y: event.y };
  return { type: "tilt_updated" };
}

function reanchorTargets(state, now) {
  for (const target of state.targets) {
    Object.assign(target, positionAt(target, now, state), { positionedAt: now });
  }
}
