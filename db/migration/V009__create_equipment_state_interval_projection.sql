-- Equipment State dwell intervals (ADR-050). A processing run is immutable: reprocessing a longer
-- input writes a new row and leaves earlier intervals exactly as they were.
CREATE TABLE equipment_state_interval_processing (
    processing_run_id TEXT PRIMARY KEY CHECK (
        processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    through_replay_sequence BIGINT NOT NULL CHECK (through_replay_sequence >= 0),
    interval_rule_version TEXT NOT NULL,
    observed_from TIMESTAMPTZ NOT NULL,
    observed_to TIMESTAMPTZ NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    input_observation_count INTEGER NOT NULL CHECK (input_observation_count > 0),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    coverage JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT equipment_state_interval_range_check CHECK (observed_to >= observed_from)
);

CREATE INDEX equipment_state_interval_processing_source_idx
    ON equipment_state_interval_processing (
        machine_id, replay_session_id, through_replay_sequence
    );

CREATE TABLE equipment_state_interval (
    processing_run_id TEXT NOT NULL,
    signal TEXT NOT NULL CHECK (
        signal IN ('EXECUTION', 'CONTROLLER_MODE', 'POWER_STATE', 'EMERGENCY_STOP')
    ),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    -- NULL is an interval the source reported as UNAVAILABLE, never a gap filled in by the policy.
    state_value TEXT,
    start_replay_sequence BIGINT NOT NULL CHECK (start_replay_sequence >= 0),
    start_source_event_key TEXT NOT NULL,
    end_replay_sequence BIGINT CHECK (end_replay_sequence >= 0),
    end_source_event_key TEXT,
    PRIMARY KEY (processing_run_id, signal, started_at, start_source_event_key),
    CONSTRAINT equipment_state_interval_processing_fk
        FOREIGN KEY (processing_run_id)
        REFERENCES equipment_state_interval_processing (processing_run_id),
    CONSTRAINT equipment_state_interval_time_check CHECK (
        ended_at IS NULL OR ended_at >= started_at
    ),
    -- An open interval carries no end evidence, and a closed one cannot be missing it.
    CONSTRAINT equipment_state_interval_end_evidence_check CHECK (
        (ended_at IS NULL AND end_replay_sequence IS NULL AND end_source_event_key IS NULL)
        OR (ended_at IS NOT NULL AND end_replay_sequence IS NOT NULL
            AND end_source_event_key IS NOT NULL)
    )
);

CREATE INDEX equipment_state_interval_order_idx
    ON equipment_state_interval (processing_run_id, signal, started_at);
