# Canonical Observation v2 fixtures

These `DERIVED_FIXTURE` examples use the pinned NIST Mazak01 source and preserve its component and
DataItem identities. Valid fixtures cover SAMPLE, EVENT, CONDITION, replay, and unavailable values.
Invalid fixtures prove that missing subject/provenance identity, version drift, unit mismatch, and
unknown fields are rejected.

Valid fixtures span both supported envelope versions: `2.0.0` documents stay valid, and the
`2.1.0` documents carry the accumulated-time metric and the emergency-stop Event added with that
version. `accumulated-time-before-schema-upgrade.json` and `event-line-value-type.json` prove that
the new vocabulary is rejected under `2.0.0` and that whole-count Events reject string values.
