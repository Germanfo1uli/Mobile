ALTER TABLE players
    ADD COLUMN IF NOT EXISTS selected_theurgy varchar(40) NOT NULL DEFAULT 'gravity';

CREATE TABLE IF NOT EXISTS player_theurgies (
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    theurgy_id varchar(40) NOT NULL,
    purchased_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (player_id, theurgy_id),
    CONSTRAINT player_theurgies_known_id CHECK (theurgy_id IN ('chrono', 'scarlet'))
);

CREATE INDEX IF NOT EXISTS player_theurgies_player_idx
    ON player_theurgies (player_id, purchased_at);
