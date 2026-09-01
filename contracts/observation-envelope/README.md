# Observation Envelope Contracts

`v1/observation-envelope.schema.json` is the single language-neutral contract for NIST-backed
Canonical Observations. It uses JSON Schema Draft 2020-12 constructs supported by OpenAPI 3.1.

## Version and compatibility policy

- `schemaVersion` is exactly `1.0.0`; consumers reject every other version explicitly.
- Every object is closed. Unknown fields are rejected instead of being silently ignored.
- Adding, removing, or changing a field requires a new schema version and an explicit producer and
  consumer upgrade. A new schema never changes an existing versioned file in place.
- SAMPLE, EVENT, and CONDITION are separate payload shapes selected by `observationKind`.
- `source.sourceObservedAt` is always required. `replay` is absent before replay and, when present,
  contains session identity, sequence, and publication time as one complete group.

## Provenance and unavailable values

The v1 contract is grounded in the pinned NIST Mazak01 source profile. `provenance.source` identifies
the source set and immutable artifact; `provenance.transformation` identifies the Raw Record and
mapping version. Evidence state is intentionally not part of provenance.

SAMPLE and EVENT payloads preserve their original category when the source reports `UNAVAILABLE`.
Such payloads use `availability: UNAVAILABLE` and must not contain an invented value or unit.
CONDITION uses `UNAVAILABLE` as its condition level and is never treated as an Alarm.

Shared fixtures live under `tests/fixtures/canonical/v1`. Edge producer and Factory API consumer
tests both validate those exact files against this schema.
