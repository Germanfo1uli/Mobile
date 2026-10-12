ALTER TABLE multiplayer_matches
    ADD COLUMN mode varchar(12) NOT NULL DEFAULT 'private'
    CHECK (mode IN ('private', 'quick'));

ALTER TABLE multiplayer_match_players
    ADD COLUMN round_id uuid UNIQUE REFERENCES game_rounds(id) ON DELETE SET NULL;

ALTER TABLE multiplayer_match_results
    ADD COLUMN round_id uuid UNIQUE REFERENCES game_rounds(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS multiplayer_quick_waiting_idx
    ON multiplayer_matches (created_at)
    WHERE mode = 'quick' AND status = 'waiting';
