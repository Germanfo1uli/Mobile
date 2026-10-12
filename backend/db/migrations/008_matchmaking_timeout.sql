ALTER TABLE multiplayer_matches
    DROP CONSTRAINT IF EXISTS multiplayer_matches_status_check;

ALTER TABLE multiplayer_matches
    ADD CONSTRAINT multiplayer_matches_status_check
    CHECK (status IN ('waiting', 'running', 'finished', 'expired'));
