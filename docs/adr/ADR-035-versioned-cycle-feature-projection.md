# ADR-035: Versioned Cycle Feature Projection

- Status: Accepted
- Date: 2026-09-04
- Related PRD: 6~7, 63~66, 120
- Extends: [ADR-032](./ADR-032-observed-process-analytics-boundary.md),
  [ADR-034](./ADR-034-deterministic-machining-run-segmentation.md)

## Context

Machining Run segmentation establishes source-time intervals but does not provide comparable cycle
statistics. Consumers must not repeatedly interpret raw telemetry, fill unknown periods with zero,
or guess a representative spindle when the source exposes multiple channels. A reproducible
projection needs an explicit calculation version, coverage, observation range, and nested source
provenance.

## Decision

- Cycle Feature version `1.0.0` accepts only terminal `COMPLETED` Machining Runs. Other run states
  remain in the source result but do not produce a feature set.
- Aggregate the half-open source-time window `[startedAt, endedAt)`. Preserve duration to nine
  decimal seconds. A zero-duration run is `EMPTY_WINDOW`; calculated statistics and coverage ratio
  are null.
- Carry the latest same-session observation forward until the next observation or window end.
  `EXECUTION=ACTIVE` is cutting. A known available non-`ACTIVE` execution value is idle. Unknown or
  unavailable state is uncovered and is never classified as idle.
- Keep numeric observations separate by metric, `componentId`, `sourceDataItemId`, and unit. Report
  RPM, load, and path feedrate using time-weighted mean, maximum in the original unit, and population
  standard deviation. Round mean and standard deviation to six decimals with `HALF_UP`.
- `UNAVAILABLE` ends coverage until the next available observation. Do not replace missing samples
  with zero. Report `AVAILABLE`, `PARTIAL`, `MISSING`, or `EMPTY_WINDOW` with covered seconds, window
  seconds, and ratio.
- Reject more than one unit for the same metric/component/source-data-item input with stable error
  `CYCLE_FEATURE_UNIT_MISMATCH`; do not preserve a partial processing result.
- Derive processing, feature-set, input, and result hashes from deterministic normalized values with
  UTF-8 byte-length-prefixed fields. Creation wall time is excluded. Identical input reuses the
  immutable result; a late relevant observation creates a new result.
- Preserve processing metadata and all JSONB feature projections in one PostgreSQL transaction.
  Existing Machining Runs and Canonical Observations are never updated.
- Expose creation/reuse and immutable lookup through the Cycle Feature v1 REST/schema contract.
  Preserve `DERIVED` transformation provenance and nested `REAL:NIST` observation provenance.

## Consequences

- A consumer can compare stored feature sets without querying Canonical Observation history.
- Coverage makes missing state and metric periods visible, so averages are only over observed
  duration and are not claims about uncovered time.
- Multiple physical or semantic channels remain independently reviewable. A future channel-selection
  policy requires a new explicit decision and contract version.
- Changes to calculation, rounding, carry-in, or availability semantics require a new Cycle Feature
  version and coexist with earlier projections.

## Verification

- Pure extractor tests cover the ten-second golden calculation, partial coverage, carry-in,
  unavailable gaps without units, missing metrics, zero-duration windows, and unit mismatch.
- Application tests cover completed-only eligibility, identical-input reuse, late-input versioning,
  deterministic hashes, and empty eligible results.
- Producer and independent Python consumer validate the same v1 schema and reviewed fixture.
- PostgreSQL integration tests cover migration, atomic insert/query, reuse, late-input coexistence,
  source preservation, and rollback after projection failure.
