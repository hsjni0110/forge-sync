# Observation Envelope Contracts

`v2/observation-envelope.schema.json` is the single language-neutral contract for NIST-backed
Canonical Observations. It uses JSON Schema Draft 2020-12 constructs supported by OpenAPI 3.1.

## Version and compatibility policy

- `schemaVersion` is `2.0.0` or `2.1.0`; consumers reject every other version explicitly.
- Every object is closed. Unknown fields are rejected instead of being silently ignored.
- Adding, removing, or changing a field requires a new schema version and an explicit producer and
  consumer upgrade. A new schema never changes an existing versioned file in place.
- `2.1.0` adds canonical vocabulary: the `TOTAL_ACCUMULATED_TIME`, `AUTO_ACCUMULATED_TIME`, and
  `CUT_ACCUMULATED_TIME` metrics with the `SECOND` unit, and the `EMERGENCY_STOP`,
  `PROGRAMMED_PATH_FEEDRATE_OVERRIDE`, `RAPID_PATH_FEEDRATE_OVERRIDE`,
  `ROTARY_VELOCITY_OVERRIDE`, `LINE`, and `PROGRAM_SEQUENCE_NUMBER` Event types.
- `2.1.0` also adds one field: an available SAMPLE payload carries `unitProvenance`, either
  `SOURCE_DECLARED` or `DERIVED`. A unit the source catalog never declared must not look like one
  it did, and the mapping table that records the derivation never crosses the ingestion boundary,
  so the envelope states it. An unavailable SAMPLE has no value, unit, or unit provenance.
- Both additions belong to `2.1.0` alone. A document that declares `2.0.0` and carries a `2.1.0`
  metric, Event type, or `unitProvenance` is rejected, so the declared version always describes
  the payload, and documents already stored under `2.0.0` stay valid and replayable.
- Producers emit the highest version they can populate. The MQTT `schema-version` delivery
  property repeats the payload `schemaVersion` instead of a build-time constant.
- SAMPLE, EVENT, and CONDITION are separate payload shapes selected by `observationKind`.
- `source.sourceObservedAt` is always required. `replay` is absent before replay and, when present,
  contains session identity, sequence, and publication time as one complete group.

## Provenance and unavailable values

The v2 contract is grounded in the pinned NIST Mazak01 source profile. `subject.componentId`
identifies the observed component. `provenance.source` identifies
the source set and immutable artifact; `provenance.transformation` identifies the Raw Record and
mapping version and source DataItem. Evidence state is intentionally not part of provenance.

SAMPLE and EVENT payloads preserve their original category when the source reports `UNAVAILABLE`.
Such payloads use `availability: UNAVAILABLE` and must not contain an invented value or unit.
CONDITION uses `UNAVAILABLE` as its condition level and is never treated as an Alarm.

Shared fixtures live under `tests/fixtures/canonical/v2`. Edge producer and Factory API consumer
tests both validate those exact files against this schema.
