CREATE TABLE ingestion_inbox (
    replay_session_id UUID NOT NULL,
    source_event_key TEXT NOT NULL,
    event_id UUID NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (replay_session_id, source_event_key)
);

CREATE TABLE canonical_observation_history (
    event_id UUID NOT NULL,
    replay_session_id UUID NOT NULL,
    source_event_key TEXT NOT NULL,
    schema_version TEXT NOT NULL CHECK (schema_version = '2.0.0'),
    machine_id TEXT NOT NULL,
    component_id TEXT NOT NULL,
    observation_kind TEXT NOT NULL CHECK (observation_kind IN ('SAMPLE', 'EVENT', 'CONDITION')),
    source_observed_at TIMESTAMPTZ NOT NULL,
    replay_sequence BIGINT NOT NULL CHECK (replay_sequence >= 0),
    replay_published_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    artifact_id TEXT NOT NULL CHECK (artifact_id ~ '^sha256:[0-9a-f]{64}$'),
    raw_record_id TEXT NOT NULL,
    mapping_version TEXT NOT NULL,
    source_data_item_id TEXT NOT NULL,
    canonical_envelope JSONB NOT NULL,
    PRIMARY KEY (replay_session_id, source_event_key),
    CONSTRAINT canonical_observation_inbox_fk
        FOREIGN KEY (replay_session_id, source_event_key)
        REFERENCES ingestion_inbox (replay_session_id, source_event_key)
);

CREATE INDEX canonical_observation_machine_ordering_idx
    ON canonical_observation_history (
        machine_id, replay_session_id, replay_sequence, source_observed_at, source_event_key
    );

CREATE INDEX canonical_observation_raw_record_idx
    ON canonical_observation_history (raw_record_id);

CREATE INDEX canonical_observation_event_idx
    ON canonical_observation_history (event_id);
