CREATE TABLE cycle_feature_processing_run (
    feature_processing_run_id TEXT PRIMARY KEY CHECK (
        feature_processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machining_run_processing_run_id TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    cycle_feature_version TEXT NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    input_observation_count INTEGER NOT NULL CHECK (input_observation_count >= 0),
    eligible_run_count INTEGER NOT NULL CHECK (eligible_run_count >= 0),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT cycle_feature_machining_run_fk
        FOREIGN KEY (machining_run_processing_run_id)
        REFERENCES process_analytics_processing_run (processing_run_id)
);

CREATE INDEX cycle_feature_processing_source_idx
    ON cycle_feature_processing_run (machine_id, machining_run_processing_run_id);

CREATE TABLE cycle_feature_projection (
    feature_processing_run_id TEXT NOT NULL,
    cycle_feature_set_id TEXT NOT NULL CHECK (
        cycle_feature_set_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    machining_run_id TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    feature_status TEXT NOT NULL CHECK (
        feature_status IN ('AVAILABLE', 'PARTIAL', 'MISSING', 'EMPTY_WINDOW')
    ),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    feature_projection JSONB NOT NULL,
    PRIMARY KEY (feature_processing_run_id, cycle_feature_set_id),
    CONSTRAINT cycle_feature_processing_fk
        FOREIGN KEY (feature_processing_run_id)
        REFERENCES cycle_feature_processing_run (feature_processing_run_id),
    CONSTRAINT cycle_feature_window_check CHECK (ended_at >= started_at)
);

CREATE INDEX cycle_feature_projection_order_idx
    ON cycle_feature_projection (machine_id, feature_processing_run_id, started_at, cycle_feature_set_id);
