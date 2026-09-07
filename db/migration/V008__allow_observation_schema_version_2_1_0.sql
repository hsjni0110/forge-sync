-- Observation envelope 2.1.0 only adds canonical vocabulary, so stored 2.0.0 history stays valid.
ALTER TABLE canonical_observation_history
    DROP CONSTRAINT canonical_observation_history_schema_version_check;

ALTER TABLE canonical_observation_history
    ADD CONSTRAINT canonical_observation_history_schema_version_check
        CHECK (schema_version IN ('2.0.0', '2.1.0'));
