CREATE TABLE player_pvp_rating (
    player_id uuid PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    points integer NOT NULL DEFAULT 0 CHECK (points BETWEEN 0 AND 1000),
    highest_reward_tier smallint NOT NULL DEFAULT 0 CHECK (highest_reward_tier BETWEEN 0 AND 10),
    wins integer NOT NULL DEFAULT 0,
    losses integer NOT NULL DEFAULT 0,
    draws integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE pvp_rating_events (
    match_id uuid NOT NULL REFERENCES multiplayer_matches(id) ON DELETE CASCADE,
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    delta integer NOT NULL,
    points_before integer NOT NULL,
    points_after integer NOT NULL,
    reward_clues bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, player_id)
);

ALTER TABLE multiplayer_matches
    ADD COLUMN rank_tier smallint NOT NULL DEFAULT 0 CHECK (rank_tier BETWEEN 0 AND 10),
    ADD COLUMN gold_rate numeric(12,2);

CREATE INDEX player_pvp_rating_order_idx
    ON player_pvp_rating (points DESC, wins DESC, updated_at ASC);
