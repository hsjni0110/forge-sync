# ADR-027: Equipment State and Freshness Projection

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 14, 16~18, 24, 26, 34, 36, 99
- Extends: [ADR-026](./ADR-026-latest-observation-ordering.md)

## Context

Latest Observations preserve source categories and values but are not yet a human-readable Equipment
State. The selected NIST source has no mapped AVAILABILITY records, while it does provide valid
Sample, Event, and Condition records. Treating historical source time as live freshness would also
make every replayed Observation stale immediately.

Equipment State must be derived only from the current Latest Observation set. A replaced Condition
or Execution value must stop influencing the state, and an out-of-order Observation that does not
change Latest Observation must not roll state back. Unknown and unavailable input must not create a
false ONLINE, NORMAL, or known execution state.

## Decision

- Rebuild machine Equipment State from all current Latest Observation rows after an accepted Latest
  Observation change. Keep JSON parsing in the PostgreSQL Adapter and pass typed values to a pure
  Domain Policy.
- Use `ONLINE` when at least one current Observation has an available value or a known Condition
  level. Use `UNKNOWN` when there are no current available Observations. Do not infer `OFFLINE` from
  missing replay data.
- Map only `READY`, `ACTIVE`, `IDLE`, `HOLD`, and `STOPPED` EXECUTION values. Unavailable,
  unsupported, or conflicting current EXECUTION values produce `UNKNOWN`.
- Aggregate current Condition levels as `FAULT`, then `WARNING`, then `NORMAL`. Report `NORMAL` only
  when every current Condition is NORMAL. No Condition, or UNAVAILABLE without a known warning or
  fault, produces `UNKNOWN`. Condition remains distinct from Alarm.
- Persist base Equipment State with the same machine TwinVersion and `projectedAt` as the accepted
  Latest Observation change. Late and duplicate deliveries do not update either projection.
- Compute Freshness at read time from `evaluatedAt - projectedAt`; never persist a time-decaying
  freshness label. Defaults are FRESH at `<=2s`, LAGGING at `>2s..10s`, and STALE at `>10s`.
  Thresholds are configurable and invalid windows or clock regression are rejected.
- When base connectivity is ONLINE and Freshness is STALE, expose effective connectivity as STALE.
  Historical `sourceObservedAt` is not a freshness input.
- Extend the ingestion transaction through Equipment State projection. A state projection failure
  rolls back Inbox, Observation history, Latest Observation, TwinVersion, and Equipment State.

## Consequences

- Replayed historical data is FRESH immediately after projection and ages with wall clock time.
- The current NIST source can establish ONLINE from valid telemetry without inventing an absent
  AVAILABILITY value. An all-unavailable current set remains explicitly UNKNOWN.
- Rebuilding from the current rows is simple and deterministic for the MVP, at the cost of reading
  all Latest Observations for one machine after an accepted update.
- REST, WebSocket, and Presentation consumers can later use the explicit STALE state to stop live
  animation without owning freshness rules.

## Verification

- Pure unit tests cover the exact 2s/10s boundaries, historical source time separation, invalid
  windows, execution mapping, unavailable input, Condition aggregation, and effective STALE state.
- `./scripts/verify-database` verifies state/version consistency, unavailable projection, Condition
  aggregation, out-of-order protection, and full rollback when Equipment State persistence fails.
