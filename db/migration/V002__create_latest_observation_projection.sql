CREATE TABLE equipment_twin_version (
    machine_id TEXT PRIMARY KEY,
    twin_version BIGINT NOT NULL CHECK (twin_version >= 0),
    projected_at TIMESTAMPTZ,
    CONSTRAINT equipment_twin_version_time_check CHECK (
        (twin_version = 0 AND projected_at IS NULL)
        OR (twin_version > 0 AND projected_at IS NOT NULL)
    )
);

CREATE TABLE latest_observation_projection (
    machine_id TEXT NOT NULL,
    source_data_item_id TEXT NOT NULL,
    event_id UUID NOT NULL,
    component_id TEXT NOT NULL,
    observation_kind TEXT NOT NULL CHECK (observation_kind IN ('SAMPLE', 'EVENT', 'CONDITION')),
    replay_session_id UUID NOT NULL,
    replay_sequence BIGINT NOT NULL CHECK (replay_sequence >= 0),
    source_observed_at TIMESTAMPTZ NOT NULL,
    source_event_key TEXT NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    twin_version BIGINT NOT NULL CHECK (twin_version > 0),
    artifact_id TEXT NOT NULL CHECK (artifact_id ~ '^sha256:[0-9a-f]{64}$'),
    raw_record_id TEXT NOT NULL,
    mapping_version TEXT NOT NULL,
    canonical_envelope JSONB NOT NULL,
    PRIMARY KEY (machine_id, source_data_item_id),
    CONSTRAINT latest_observation_machine_fk
        FOREIGN KEY (machine_id) REFERENCES equipment_twin_version (machine_id)
);

CREATE INDEX latest_observation_kind_idx
    ON latest_observation_projection (machine_id, observation_kind);
