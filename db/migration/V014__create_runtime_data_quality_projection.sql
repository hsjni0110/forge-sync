CREATE TABLE runtime_data_quality_projection (
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    received_count BIGINT NOT NULL CHECK (received_count >= 0),
    accepted_count BIGINT NOT NULL CHECK (accepted_count >= 0),
    duplicate_count BIGINT NOT NULL CHECK (duplicate_count >= 0),
    out_of_order_count BIGINT NOT NULL CHECK (out_of_order_count >= 0),
    max_replay_sequence BIGINT NOT NULL CHECK (max_replay_sequence >= 0),
    max_source_observed_at TIMESTAMPTZ NOT NULL,
    max_source_event_key TEXT NOT NULL,
    first_ingested_at TIMESTAMPTZ NOT NULL,
    last_ingested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (machine_id, replay_session_id),
    CONSTRAINT runtime_data_quality_count_check CHECK (
        accepted_count + duplicate_count = received_count
    ),
    CONSTRAINT runtime_data_quality_time_check CHECK (last_ingested_at >= first_ingested_at)
);

CREATE TABLE runtime_data_quality_observation (
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    source_event_key TEXT NOT NULL,
    replay_sequence BIGINT NOT NULL CHECK (replay_sequence >= 0),
    received_count BIGINT NOT NULL CHECK (received_count >= 1),
    accepted_count BIGINT NOT NULL CHECK (accepted_count IN (0, 1)),
    duplicate_count BIGINT NOT NULL CHECK (duplicate_count >= 0),
    out_of_order_count BIGINT NOT NULL CHECK (out_of_order_count IN (0, 1)),
    first_ingested_at TIMESTAMPTZ NOT NULL,
    last_ingested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (machine_id, replay_session_id, source_event_key),
    CONSTRAINT runtime_data_quality_observation_count_check CHECK (
        accepted_count + duplicate_count = received_count
    ),
    CONSTRAINT runtime_data_quality_observation_time_check CHECK (
        last_ingested_at >= first_ingested_at
    )
);

CREATE INDEX runtime_data_quality_observation_cursor_idx
    ON runtime_data_quality_observation (machine_id, replay_session_id, replay_sequence);
