CREATE TABLE operational_effectiveness_processing (
    processing_run_id TEXT PRIMARY KEY CHECK (processing_run_id ~ '^sha256:[0-9a-f]{64}$'),
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    through_replay_sequence BIGINT NOT NULL CHECK (through_replay_sequence >= 0),
    policy_version TEXT NOT NULL,
    utilization_processing_run_id TEXT NOT NULL REFERENCES utilization_kpi_processing(processing_run_id),
    cycle_feature_processing_run_id TEXT NOT NULL REFERENCES cycle_feature_processing_run(feature_processing_run_id),
    observed_from TIMESTAMPTZ NOT NULL,
    observed_to TIMESTAMPTZ NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT operational_effectiveness_observed_range_check CHECK (observed_to >= observed_from)
);

CREATE INDEX operational_effectiveness_source_idx
    ON operational_effectiveness_processing(machine_id, replay_session_id, through_replay_sequence, created_at);
