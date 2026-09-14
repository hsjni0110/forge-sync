CREATE TABLE condition_projection (
    replay_session_id UUID NOT NULL,
    source_event_key TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    replay_sequence BIGINT NOT NULL CHECK (replay_sequence >= 0),
    source_observed_at TIMESTAMPTZ NOT NULL,
    source_data_item_id TEXT NOT NULL,
    component_id TEXT NOT NULL,
    condition_type TEXT NOT NULL,
    level TEXT NOT NULL CHECK (level IN ('NORMAL', 'WARNING', 'FAULT', 'UNAVAILABLE')),
    native_code TEXT,
    message TEXT,
    projected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (replay_session_id, source_event_key)
);

CREATE INDEX condition_projection_machine_ordering_idx
    ON condition_projection (machine_id, replay_session_id, replay_sequence);

CREATE TABLE alarm (
    alarm_id UUID PRIMARY KEY,
    machine_id TEXT NOT NULL,
    replay_session_id UUID NOT NULL,
    opened_replay_sequence BIGINT NOT NULL CHECK (opened_replay_sequence >= 0),
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by_source_event_key TEXT NOT NULL,
    source_data_item_id TEXT NOT NULL,
    component_id TEXT NOT NULL,
    condition_type TEXT NOT NULL,
    native_code TEXT NOT NULL,
    message TEXT,
    severity TEXT NOT NULL CHECK (severity IN ('WARNING', 'CRITICAL')),
    status TEXT NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    rule_version TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    acknowledged_by TEXT,
    acknowledged_at TIMESTAMPTZ,
    resolved_by_source_event_key TEXT,
    resolved_replay_sequence BIGINT CHECK (resolved_replay_sequence >= opened_replay_sequence),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT alarm_open_condition_fk
        FOREIGN KEY (replay_session_id, opened_by_source_event_key)
        REFERENCES condition_projection (replay_session_id, source_event_key),
    CONSTRAINT alarm_acknowledgement_pair_check CHECK (
        (acknowledged_by IS NULL AND acknowledged_at IS NULL)
        OR (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL)
    ),
    CONSTRAINT alarm_resolution_pair_check CHECK (
        (resolved_by_source_event_key IS NULL AND resolved_replay_sequence IS NULL AND resolved_at IS NULL)
        OR (resolved_by_source_event_key IS NOT NULL AND resolved_replay_sequence IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX alarm_one_active_native_code_idx
    ON alarm (machine_id, replay_session_id, source_data_item_id, native_code)
    WHERE status <> 'RESOLVED';

CREATE INDEX alarm_machine_session_ordering_idx
    ON alarm (machine_id, replay_session_id, opened_replay_sequence, alarm_id);

CREATE TABLE business_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX business_outbox_unpublished_idx
    ON business_outbox (occurred_at, event_id)
    WHERE published_at IS NULL;
