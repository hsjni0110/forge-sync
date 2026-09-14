# ADR-055: Condition-derived Business Alarm Lifecycle

- Status: Accepted
- Date: 2026-09-13
- Related PRD: 38, 118
- Extends: [ADR-025](./ADR-025-postgres-ingestion-transaction.md),
  [ADR-029](./ADR-029-websocket-twin-patch-resync.md)

## Context

The fixed NIST Mazak01 source contains 1,052 Condition observations. A Condition is source evidence,
not automatically an operator-facing Alarm, and an Anomaly Assessment is a separate derived result.
Promoting every non-normal value would erase those distinctions and create unsupported alarms.

The inspected source has 17 `WARNING` observations and no `FAULT` observation. The warnings use
native codes 345, 401, 406, 442, 468, 1101, and 1105 on `Mazak01-controller_2` or
`Mazak01-controller_3`. Some matching `NORMAL` observations name a native code; others omit it.

## Decision

- Preserve every canonical Condition in `condition_projection` before applying alarm policy.
- Rule `1.0.0` explicitly allowlists the observed DataItem/native-code pairs. An allowlisted
  `WARNING` opens a `WARNING` Alarm; an allowlisted future `FAULT` opens a `CRITICAL` Alarm. Other
  Conditions, `UNAVAILABLE`, and every Anomaly Assessment do not open an Alarm.
- The active identity is machine, replay session, source DataItem, and native code. Repeated source
  warnings do not multiply an OPEN or ACKNOWLEDGED Alarm. A later warning after resolution opens a
  new Alarm with a new deterministic ID.
- A matching `NORMAL` with a native code resolves that code. A code-less `NORMAL` resolves all
  active Alarms for its source DataItem. `UNAVAILABLE` never resolves an Alarm.
- The aggregate lifecycle is OPEN → ACKNOWLEDGED → RESOLVED or OPEN → RESOLVED. ACK requires a
  nonblank operator name and injected wall-clock time. Source NORMAL resolves both OPEN and
  ACKNOWLEDGED states. Repeated ACK and resolution are idempotent; ACK after resolution is invalid.
- Opening, acknowledgement, and resolution write versioned business Outbox events in the same
  PostgreSQL transaction as their Alarm change. Raw telemetry is not copied to the Outbox.
- Alarm query and ACK use the dedicated `application/vnd.forgesync.alarms.v1+json` boundary rather
  than making Equipment Twin own the Alarm aggregate. The query is scoped by replay session and
  replay sequence. Open and resolved replay sequences are retained.
- Presentation reuses the same `alarmId` and `machineId` in the 2D list, spatial marker, and shift
  timeline marker. Text and symbols accompany colour. A 3D machine selection reveals the 2D detail
  panel; WebGL failure does not remove the 2D Alarm workflow.

## Consequences

- Source Condition evidence and business response state remain independently queryable.
- Adding or changing a native-code rule requires an explicit rule-version change and source
  evidence review; inferred message matching is not accepted.
- The current fixed source exercises WARNING but not CRITICAL creation. CRITICAL behavior is fixed
  by policy and presentation tests without claiming that the dataset contains a fault example.
- The Outbox rows are durable publication candidates; this step does not claim a broker-level
  exactly-once guarantee.

## Verification

- Domain tests cover allowlist boundaries, NORMAL scopes, lifecycle transitions, idempotency, and
  replay-sequence preservation.
- PostgreSQL tests cover Condition/Alarm separation, duplicates, resolution, transactional Outbox,
  cursor query, named ACK, repeat ACK, and stale revision rejection.
- Shared contract, REST, frontend client, component, route, and 3D renderer tests keep one Alarm
  identity across 2D, 3D, and timeline views with non-colour cues.
