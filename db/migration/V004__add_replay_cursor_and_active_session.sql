ALTER TABLE equipment_twin_version
    ADD COLUMN replay_session_id UUID,
    ADD COLUMN replay_sequence BIGINT,
    ADD COLUMN source_observed_at TIMESTAMPTZ,
    ADD COLUMN replay_published_at TIMESTAMPTZ;

UPDATE equipment_twin_version version
SET replay_session_id = latest.replay_session_id,
    replay_sequence = latest.replay_sequence,
    source_observed_at = latest.source_observed_at,
    replay_published_at = (latest.canonical_envelope #>> '{replay,replayPublishedAt}')::timestamptz
FROM (
    SELECT DISTINCT ON (machine_id)
        machine_id, replay_session_id, replay_sequence, source_observed_at, canonical_envelope
    FROM latest_observation_projection
    ORDER BY machine_id, twin_version DESC
) latest
WHERE version.machine_id = latest.machine_id;

ALTER TABLE equipment_twin_version
    ADD CONSTRAINT equipment_twin_replay_cursor_check CHECK (
        (twin_version = 0 AND replay_session_id IS NULL AND replay_sequence IS NULL
            AND source_observed_at IS NULL AND replay_published_at IS NULL)
        OR
        (twin_version > 0 AND replay_session_id IS NOT NULL AND replay_sequence >= 0
            AND source_observed_at IS NOT NULL AND replay_published_at IS NOT NULL)
    );

CREATE TABLE active_replay_projection (
    machine_id TEXT PRIMARY KEY,
    replay_session_id UUID NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL
);
