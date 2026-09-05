CREATE TABLE anomaly_assessment_processing_run (
    assessment_processing_run_id TEXT PRIMARY KEY CHECK (
        assessment_processing_run_id ~ '^sha256:[0-9a-f]{64}$'
    ),
    cycle_feature_processing_run_id TEXT NOT NULL,
    machining_run_processing_run_id TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    cycle_feature_version TEXT NOT NULL,
    baseline_policy_version TEXT NOT NULL,
    anomaly_assessment_version TEXT NOT NULL,
    input_hash TEXT NOT NULL CHECK (input_hash ~ '^sha256:[0-9a-f]{64}$'),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT anomaly_assessment_cycle_feature_fk
        FOREIGN KEY (cycle_feature_processing_run_id)
        REFERENCES cycle_feature_processing_run (feature_processing_run_id),
    CONSTRAINT anomaly_assessment_machining_run_fk
        FOREIGN KEY (machining_run_processing_run_id)
        REFERENCES process_analytics_processing_run (processing_run_id)
);

CREATE INDEX anomaly_assessment_processing_source_idx
    ON anomaly_assessment_processing_run (machine_id, cycle_feature_processing_run_id);

CREATE TABLE anomaly_assessment_projection (
    assessment_processing_run_id TEXT NOT NULL,
    assessment_id TEXT NOT NULL CHECK (assessment_id ~ '^sha256:[0-9a-f]{64}$'),
    machining_run_id TEXT NOT NULL,
    target_feature_set_id TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    target_started_at TIMESTAMPTZ NOT NULL,
    data_status TEXT NOT NULL CHECK (
        data_status IN ('UNAVAILABLE', 'INSUFFICIENT_DATA', 'PARTIAL', 'AVAILABLE')
    ),
    classification TEXT CHECK (
        classification IS NULL OR classification IN ('NORMAL', 'DEVIATING', 'HIGH_DEVIATION')
    ),
    score NUMERIC(7, 6) CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    result_hash TEXT NOT NULL CHECK (result_hash ~ '^sha256:[0-9a-f]{64}$'),
    assessment_projection JSONB NOT NULL,
    PRIMARY KEY (assessment_processing_run_id, assessment_id),
    CONSTRAINT anomaly_assessment_processing_fk
        FOREIGN KEY (assessment_processing_run_id)
        REFERENCES anomaly_assessment_processing_run (assessment_processing_run_id)
);

CREATE INDEX anomaly_assessment_projection_order_idx
    ON anomaly_assessment_projection (
        machine_id, assessment_processing_run_id, target_started_at, assessment_id
    );
