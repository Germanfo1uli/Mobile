ALTER TABLE players
    ADD COLUMN IF NOT EXISTS clues bigint NOT NULL DEFAULT 0 CHECK (clues >= 0);

ALTER TABLE game_results
    ADD COLUMN IF NOT EXISTS level smallint NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 3);

UPDATE players p
SET clues = COALESCE((
    SELECT SUM(r.score)
    FROM game_results r
    WHERE r.player_id = p.id AND r.verified = true
), 0);

CREATE INDEX IF NOT EXISTS game_results_player_level_score_idx
    ON game_results (player_id, level, score DESC)
    WHERE verified = true;
