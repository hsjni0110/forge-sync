CREATE TABLE process_analytics_processing_run (
    processing_run_id TEXT PRIMARY KEY CHECK (
        processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    through_replay_sequence BIGINT NOT NULL CHECK (through_replay_sequence >= 0),
    segmentation_rule_version TEXT NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    input_observation_count INTEGER NOT NULL CHECK (input_observation_count > 0),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX process_analytics_processing_source_idx
    ON process_analytics_processing_run (
        machine_id, replay_session_id, through_replay_sequence
    );

CREATE TABLE machining_run_projection (
    processing_run_id TEXT NOT NULL,
    machining_run_id TEXT NOT NULL CHECK (
        machining_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machine_id TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    run_status TEXT NOT NULL CHECK (
        run_status IN ('COMPLETED', 'INTERRUPTED', 'ABORTED', 'UNKNOWN')
    ),
    program_name TEXT,
    confidence TEXT NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN')),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    run_projection JSONB NOT NULL,
    PRIMARY KEY (processing_run_id, machining_run_id),
    CONSTRAINT machining_run_processing_fk
        FOREIGN KEY (processing_run_id)
        REFERENCES process_analytics_processing_run (processing_run_id),
    CONSTRAINT machining_run_time_check CHECK (
        ended_at IS NULL OR ended_at >= started_at
    )
);

CREATE INDEX machining_run_projection_order_idx
    ON machining_run_projection (machine_id, processing_run_id, started_at, machining_run_id);
