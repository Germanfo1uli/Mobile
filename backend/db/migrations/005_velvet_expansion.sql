ALTER TABLE players
    ADD COLUMN IF NOT EXISTS velvet_intro_seen boolean NOT NULL DEFAULT false;

ALTER TABLE player_theurgies
    DROP CONSTRAINT IF EXISTS player_theurgies_known_id;

ALTER TABLE player_theurgies
    ADD CONSTRAINT player_theurgies_known_id
    CHECK (theurgy_id IN ('chrono', 'scarlet', 'mirror', 'moonfall', 'armageddon'));
