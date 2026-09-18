CREATE TABLE game_rounds (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    status varchar(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'finished')),
    state jsonb NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);

CREATE UNIQUE INDEX game_rounds_one_active_player_idx
    ON game_rounds(player_id) WHERE status = 'active';
CREATE INDEX game_rounds_player_idx ON game_rounds(player_id, started_at DESC);
CREATE INDEX game_rounds_deadline_idx ON game_rounds (((state ->> 'endsAt')::bigint))
    WHERE status = 'active';

ALTER TABLE player_settings ALTER COLUMN game_speed TYPE numeric(4,2);

ALTER TABLE game_results ADD COLUMN round_id uuid REFERENCES game_rounds(id);
ALTER TABLE game_results ADD COLUMN verified boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX game_results_round_idx ON game_results(round_id);

CREATE TABLE round_events (
    round_id uuid NOT NULL REFERENCES game_rounds(id) ON DELETE CASCADE,
    event_id uuid NOT NULL,
    request jsonb NOT NULL,
    response jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (round_id, event_id)
);
