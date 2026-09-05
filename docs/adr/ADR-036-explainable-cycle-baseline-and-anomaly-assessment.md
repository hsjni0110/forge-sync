# ADR-036: Explainable Cycle Baseline and Anomaly Assessment

- Status: Accepted
- Date: 2026-09-05
- Related PRD: 6~7, 63~66, 120
- Extends: [ADR-032](./ADR-032-observed-process-analytics-boundary.md),
  [ADR-035](./ADR-035-versioned-cycle-feature-projection.md)

## Context

Cycle Features make completed Machining Runs comparable, but a score without a stable group,
training window, missing-data policy, or feature-level explanation can be mistaken for a Machine
Fault or an inferred production result. Historical source time also prevents using wall clock as a
training boundary.

## Decision

- Baseline policy `1.0.0` groups only by `machineId + programName + cycleFeatureVersion`. The
  application joins `programName` from the immutable Machining Run public result by
  `machiningRunId`; the Cycle Feature v1 contract remains unchanged.
- Evaluate every feature set in source-time order. Only same-group runs whose `startedAt` is
  strictly earlier than the target are candidates. Equal-start and future runs never train each
  other.
- For each scalar feature, use at most the 30 most recent eligible values and require at least five.
  Duration requires a non-null value. Cutting, idle, and metric values additionally require
  coverage `>= 0.800000`. Metric channels match metric, component ID, source DataItem ID, and unit
  exactly; mean, maximum, and population standard deviation are assessed separately.
- Calculate median on sorted values. For odd samples, exclude the median before calculating the
  lower and upper medians; for even samples, split into equal halves. Normalize baseline and
  assessment decimals to six places with `HALF_UP`.
- Distance is `abs(target - median) / IQR`; score is `distance / (1 + distance)`. Equal values at
  zero IQR score zero. A different target at zero IQR scores one with
  `ZERO_IQR_DEVIATION`. Classification is `NORMAL < 0.500000`,
  `DEVIATING < 0.750000`, otherwise `HIGH_DEVIATION`.
- Overall score is the maximum calculable contribution. Sort ties by stable feature key and expose
  at most three top reasons. Preserve signed difference, nullable percentage difference when the
  median is zero, direction, sample count, and contributing feature-set IDs.
- Data status precedence is `UNAVAILABLE` for a missing program or no comparable target values,
  `INSUFFICIENT_DATA` when all target features lack five samples, `PARTIAL` when only some can be
  calculated, and `AVAILABLE` when all comparable target P0 features can be calculated.
- Processing, assessment, group, input, and result identities use null-aware UTF-8 byte-length
  prefixes. Creation wall time is excluded. Identical input and versions reuse an immutable result;
  another Cycle Feature result or policy version coexists.
- Persist processing metadata and all assessment JSONB projections in one transaction. Never
  update Cycle Features, Machining Runs, Canonical Observations, Equipment Twin, Alarm,
  Intelligence Advisory, or command state.
- The v1 REST contract exposes baseline candidates and actual per-feature contributors,
  evaluation/training source ranges, and `DERIVED -> DERIVED CycleFeature -> REAL:NIST` lineage.

## Consequences

- Consumers can explain a deviation using reviewed values and exact training identities without
  rereading telemetry.
- Missing programs, insufficient samples, uncovered intervals, and channel mismatches remain
  explicit rather than being imputed.
- A high score is a rebuildable Process Analytics projection only. It does not establish equipment
  health, an Alarm, an AI Advisory, or authority to control a machine.
- Changing grouping, coverage, quartiles, limits, scoring, or classification requires a new policy
  version and must preserve earlier results.

## Verification

- Pure tests cover odd/even quartiles, the 30/5 sample bounds, the exact coverage boundary, metric
  channel identity, zero IQR, score classification, unavailable, insufficient, and partial data.
- Application tests cover program joins, all-feature-set sequential evaluation, future/equal-time
  exclusion, deterministic identities, and reuse.
- Producer and independent Python consumer validate the same schema and reviewed Mazak01 fixture.
- PostgreSQL integration tests cover migration, atomic persistence/query, reuse, source
  preservation, and rollback on projection failure. Architecture tests retain the Process
  Analytics isolation rule.
