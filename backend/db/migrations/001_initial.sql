CREATE TABLE IF NOT EXISTS schema_migrations (
    name text PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS players (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name varchar(200) NOT NULL CHECK (length(trim(full_name)) >= 2),
    gender varchar(20) NOT NULL,
    course smallint NOT NULL CHECK (course BETWEEN 1 AND 6),
    difficulty smallint NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    birth_date date NOT NULL CHECK (birth_date >= DATE '1900-01-01' AND birth_date <= CURRENT_DATE),
    zodiac varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS player_settings (
    player_id uuid PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    game_speed numeric(3,1) NOT NULL DEFAULT 1.0 CHECK (game_speed BETWEEN 0.5 AND 2.0),
    max_insects smallint NOT NULL DEFAULT 8 CHECK (max_insects BETWEEN 3 AND 15),
    bonus_interval_seconds smallint NOT NULL DEFAULT 15 CHECK (bonus_interval_seconds BETWEEN 5 AND 30),
    round_duration_seconds smallint NOT NULL DEFAULT 60 CHECK (round_duration_seconds BETWEEN 30 AND 180),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS game_results (
    id bigserial PRIMARY KEY,
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    score integer NOT NULL CHECK (score >= 0),
    hits integer NOT NULL DEFAULT 0 CHECK (hits >= 0),
    misses integer NOT NULL DEFAULT 0 CHECK (misses >= 0),
    difficulty smallint NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    played_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS game_results_leaderboard_idx
    ON game_results (score DESC, played_at ASC);
CREATE INDEX IF NOT EXISTS game_results_player_idx
    ON game_results (player_id, played_at DESC);
