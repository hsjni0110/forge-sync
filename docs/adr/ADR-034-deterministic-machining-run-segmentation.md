# ADR-034: Deterministic Machining Run Segmentation

- Status: Accepted
- Date: 2026-09-04
- Related PRD: 6~7, 63~66, 120
- Extends: [ADR-032](./ADR-032-observed-process-analytics-boundary.md)

## Context

The NIST Mazak01 source contains repeated spindle stops and short `FEED_HOLD` or source
`INTERRUPTED` execution values inside a larger active interval. Treating every zero spindle sample
or pause as a run boundary would split one observed process interval into many unsupported runs.
Canonical Observation v2 also does not carry its Canonical Processing Run ID, so Process Analytics
must identify its own reproducible input without changing that accepted contract.

## Decision

- Segmentation rule `1.0.0` is execution-led. A transition from a known non-running execution value
  to `ACTIVE` starts a confirmed run. `FEED_HOLD` and source `INTERRUPTED` keep that run open.
- `READY` completes a confirmed run. `STOPPED` without `READY` aborts it. An unavailable execution
  signal or an open input ending at its watermark interrupts it.
- An input that starts at `ACTIVE`, or spindle/program evidence without a confirmed execution
  boundary, remains `UNKNOWN` or `INTERRUPTED`; it is not upgraded by guessing earlier state.
- Program and positive spindle signals support confidence. Missing program stays absent. A zero
  spindle never ends an execution-confirmed active run. Without any available execution signal,
  zero spindle observed continuously for at least 30 source-time seconds may end a fallback run as
  `INTERRUPTED`; the later proving observation is the end anchor.
- The immutable batch input is `(machineId, replaySessionId, throughReplaySequence, ruleVersion)`
  plus the ordered relevant Canonical Observation content. Its SHA-256 is the Process Analytics
  `processingRunId`. Identical input is idempotent; added late input produces a new ID and rows.
- Relevant input is ordered by `replaySequence`, then `sourceObservedAt`, then `sourceEventKey`.
  Result IDs and hashes use stable UTF-8 hash material and do not contain wall-clock creation time.
- Results retain `DERIVED` as their transformation origin and nested `REAL:NIST` observation
  provenance. `PART_COUNT`, Production Result, and Operation Execution do not produce run results.
- PostgreSQL stores a processing record and all of its run projections atomically after the
  Ingestion transaction. Existing Canonical Observations and earlier processing versions are never
  updated.

## Consequences

- Batch processing may report an interrupted run when invoked before replay completion. Repeating
  it with a later watermark creates a separately traceable result rather than mutating that claim.
- Rule threshold or state meaning changes require a new segmentation rule and public contract
  version; runtime configuration cannot silently change version `1.0.0` behavior.
- Process Analytics remains independently rebuildable and does not extend Inbox effectively-once
  semantics or Equipment Twin ownership.

## Verification

- Pure domain tests cover confirmed completion, temporary pauses, abort, missing program,
  middle-start, open-end, and spindle-only fallback boundaries.
- Producer and independent consumer tests validate the same Machining Run v1 schema and reviewed
  NIST-derived fixture.
- PostgreSQL integration tests cover atomic storage, identical-input idempotency, late-input new
  versions, query reconstruction, and rollback on projection failure.
