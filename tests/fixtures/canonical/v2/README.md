# Canonical Observation v2 fixtures

These `DERIVED_FIXTURE` examples use the pinned NIST Mazak01 source and preserve its component and
DataItem identities. Valid fixtures cover SAMPLE, EVENT, CONDITION, replay, and unavailable values.
Invalid fixtures prove that missing subject/provenance identity, version drift, unit mismatch, and
unknown fields are rejected.

Valid fixtures span both supported envelope versions: `2.0.0` documents stay valid, and the
`2.1.0` documents carry the accumulated-time metric, the emergency-stop Event, and the
`unitProvenance` that separates a derived unit from a source-declared one.
`accumulated-time-before-schema-upgrade.json` and `unit-provenance-before-schema-upgrade.json`
prove that both additions are rejected under `2.0.0`; `sample-without-unit-provenance.json` proves
an available `2.1.0` SAMPLE cannot omit it; `event-line-value-type.json` proves whole-count Events
reject string values.
