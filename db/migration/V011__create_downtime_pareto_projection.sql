-- Immutable downtime Pareto projection derived from one utilization calculation and its interval input.
CREATE TABLE downtime_pareto_processing (
    processing_run_id TEXT PRIMARY KEY CHECK (
        processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    through_replay_sequence BIGINT NOT NULL CHECK (through_replay_sequence >= 0),
    rule_version TEXT NOT NULL,
    utilization_processing_run_id TEXT NOT NULL,
    interval_processing_run_id TEXT NOT NULL,
    observed_from TIMESTAMPTZ NOT NULL,
    observed_to TIMESTAMPTZ NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT downtime_pareto_utilization_processing_fk
        FOREIGN KEY (utilization_processing_run_id)
        REFERENCES utilization_kpi_processing (processing_run_id),
    CONSTRAINT downtime_pareto_interval_processing_fk
        FOREIGN KEY (interval_processing_run_id)
        REFERENCES equipment_state_interval_processing (processing_run_id),
    CONSTRAINT downtime_pareto_observed_range_check CHECK (observed_to >= observed_from)
);

CREATE INDEX downtime_pareto_processing_source_idx
    ON downtime_pareto_processing (
        machine_id, replay_session_id, through_replay_sequence, created_at
    );
