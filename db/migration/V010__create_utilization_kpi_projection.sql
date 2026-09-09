-- Range-scoped utilization projection. Each row refers to the immutable interval input that was
-- used and stores a new result rather than updating an earlier calculation.
CREATE TABLE utilization_kpi_processing (
    processing_run_id TEXT PRIMARY KEY CHECK (
        processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    through_replay_sequence BIGINT NOT NULL CHECK (through_replay_sequence >= 0),
    calculation_version TEXT NOT NULL,
    interval_processing_run_id TEXT NOT NULL,
    observed_from TIMESTAMPTZ NOT NULL,
    observed_to TIMESTAMPTZ NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT utilization_kpi_interval_processing_fk
        FOREIGN KEY (interval_processing_run_id)
        REFERENCES equipment_state_interval_processing (processing_run_id),
    CONSTRAINT utilization_kpi_observed_range_check CHECK (observed_to >= observed_from)
);

CREATE INDEX utilization_kpi_processing_source_idx
    ON utilization_kpi_processing (
        machine_id, replay_session_id, through_replay_sequence, created_at
    );
