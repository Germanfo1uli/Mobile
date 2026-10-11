CREATE TABLE IF NOT EXISTS multiplayer_matches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(8) NOT NULL UNIQUE,
    host_player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    status varchar(20) NOT NULL DEFAULT 'waiting' CHECK (status IN ('waiting', 'running', 'finished')),
    max_players smallint NOT NULL DEFAULT 8 CHECK (max_players BETWEEN 2 AND 16),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz
);

CREATE TABLE IF NOT EXISTS multiplayer_match_players (
    match_id uuid NOT NULL REFERENCES multiplayer_matches(id) ON DELETE CASCADE,
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, player_id)
);

CREATE TABLE IF NOT EXISTS multiplayer_match_results (
    match_id uuid NOT NULL REFERENCES multiplayer_matches(id) ON DELETE CASCADE,
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    score integer NOT NULL CHECK (score >= 0),
    hits integer NOT NULL DEFAULT 0 CHECK (hits >= 0),
    misses integer NOT NULL DEFAULT 0 CHECK (misses >= 0),
    submitted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, player_id)
);

CREATE INDEX IF NOT EXISTS multiplayer_match_players_match_idx
    ON multiplayer_match_players (match_id, joined_at);
CREATE INDEX IF NOT EXISTS multiplayer_match_results_match_idx
    ON multiplayer_match_results (match_id, score DESC, submitted_at ASC);
