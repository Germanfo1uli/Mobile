import { GameError } from "./game.js";
import { requireUuid } from "./validation.js";

export const PVP_RANKS = [
  "Новобранец", "Следопыт", "Охотник", "Фантом", "Тень", "Маска",
  "Призрак", "Легенда", "Мятежник", "Король ночи", "Икона",
];

export function pvpTier(points) {
  return Math.min(10, Math.max(0, Math.floor(points / 100)));
}

export function pvpRules(tier) {
  const safeTier = Math.min(10, Math.max(0, tier));
  return {
    rankTier: safeTier,
    rankName: PVP_RANKS[safeTier],
    gameSpeed: Number((0.8 + safeTier * 0.14).toFixed(2)),
    maxInsects: 4 + Math.floor(safeTier / 2),
    bonusIntervalSeconds: 15,
    roundDurationSeconds: 60 - safeTier * 2,
    difficulty: 3,
    goldenIntervalSeconds: 20,
  };
}

export function rewardForTier(tier) {
  return tier * 1_000;
}

export function ratingChange(points, outcome, highestRewardTier) {
  const delta = outcome === "win" ? 10 : outcome === "loss" ? -5 : 0;
  const nextPoints = Math.max(0, Math.min(1000, points + delta));
  const newTier = pvpTier(nextPoints);
  let rewardClues = 0;
  for (let tier = highestRewardTier + 1; tier <= newTier; tier += 1) {
    rewardClues += rewardForTier(tier);
  }
  return { delta: nextPoints - points, nextPoints, newTier,
    highestRewardTier: Math.max(highestRewardTier, newTier), rewardClues };
}

export async function ensurePvpRating(client, playerId) {
  await client.query(
    "INSERT INTO player_pvp_rating(player_id) VALUES ($1) ON CONFLICT DO NOTHING",
    [playerId],
  );
}

export async function applyMatchRating(client, matchId, results) {
  if (results.length !== 2) return;
  const [first, second] = results;
  const draw = first.score === second.score;
  for (const item of results) {
    await ensurePvpRating(client, item.player_id);
    const current = await client.query(
      "SELECT * FROM player_pvp_rating WHERE player_id = $1 FOR UPDATE", [item.player_id],
    );
    const other = item.player_id === first.player_id ? second : first;
    const outcome = draw ? "draw" : item.score > other.score ? "win" : "loss";
    const before = current.rows[0];
    const change = ratingChange(before.points, outcome, before.highest_reward_tier);
    const event = await client.query(
      `INSERT INTO pvp_rating_events(match_id, player_id, delta, points_before, points_after, reward_clues)
       VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT DO NOTHING RETURNING match_id`,
      [matchId, item.player_id, change.delta, before.points, change.nextPoints, change.rewardClues],
    );
    if (!event.rowCount) continue;
    await client.query(
      `UPDATE player_pvp_rating SET points = $2, highest_reward_tier = $3,
       wins = wins + $4, losses = losses + $5, draws = draws + $6, updated_at = now()
       WHERE player_id = $1`,
      [item.player_id, change.nextPoints, change.highestRewardTier,
        outcome === "win" ? 1 : 0, outcome === "loss" ? 1 : 0, outcome === "draw" ? 1 : 0],
    );
    if (change.rewardClues > 0) {
      await client.query("UPDATE players SET clues = clues + $2, updated_at = now() WHERE id = $1",
        [item.player_id, change.rewardClues]);
    }
  }
}

function ratingSnapshot(row) {
  const tier = pvpTier(row.points);
  return {
    playerId: row.player_id,
    playerName: row.full_name,
    points: row.points,
    rankTier: tier,
    rankName: PVP_RANKS[tier],
    nextRankAt: tier === 10 ? null : (tier + 1) * 100,
    highestRewardTier: row.highest_reward_tier,
    wins: row.wins,
    losses: row.losses,
    draws: row.draws,
  };
}

export function registerPvpRoutes(app, { inTransaction, pool }) {
  app.get("/api/pvp/players/:playerId", async (request, response) => {
    const playerId = requireUuid(request.params.playerId, "playerId");
    const rating = await inTransaction(async (client) => {
      const player = await client.query("SELECT id FROM players WHERE id = $1", [playerId]);
      if (!player.rowCount) throw new GameError(404, "Player not found");
      await ensurePvpRating(client, playerId);
      const current = await client.query(
        `SELECT r.*, p.full_name FROM player_pvp_rating r JOIN players p ON p.id = r.player_id
         WHERE r.player_id = $1`, [playerId],
      );
      return ratingSnapshot(current.rows[0]);
    });
    response.json({ rating, rules: pvpRules(rating.rankTier), road: PVP_RANKS.slice(1).map((name, index) => ({
      tier: index + 1, name, at: (index + 1) * 100, rewardClues: rewardForTier(index + 1),
      claimed: rating.highestRewardTier >= index + 1,
    })) });
  });

  app.get("/api/pvp/leaderboard", async (_request, response) => {
    const result = await pool.query(
      `SELECT r.*, p.full_name FROM player_pvp_rating r JOIN players p ON p.id = r.player_id
       ORDER BY r.points DESC, r.wins DESC, r.updated_at ASC LIMIT 50`,
    );
    response.json({ leaderboard: result.rows.map((row, index) => ({ place: index + 1, ...ratingSnapshot(row) })) });
  });
}
