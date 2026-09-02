# ADR-026: Latest Observation Ordering and Machine Twin Version

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 14, 16, 24, 97~99
- Extends: [ADR-025](./ADR-025-postgres-ingestion-transaction.md)
- Extended by: [ADR-027](./ADR-027-equipment-state-and-freshness.md)

## Context

Accepted Observation history must remain complete while the operational Twin resists rollback from
late delivery. Replay ordering is meaningful only inside one Replay Session, and the selected NIST
source has multiple components that can report the same semantic metric. A projection keyed only by
metric or event type would merge distinct source DataItems.

Projection and TwinVersion changes must remain atomic with the Inbox and Observation history. A
concurrent first insert has no existing projection row to lock, so locking only the DataItem row
would not protect the missing-row race.

## Decision

- Identify a Latest Observation by `(machineId, sourceDataItemId)`. Preserve component, category,
  provenance, ordering fields, and the complete Canonical envelope in the projection row.
- For two Observations in the same Replay Session, compare `replaySequence`, then
  `sourceObservedAt`, then `sourceEventKey`. Across Replay Sessions, ignore replay sequence and
  compare `sourceObservedAt`, then `sourceEventKey`.
- Do not use `replayPublishedAt`, `ingestedAt`, `projectedAt`, or `eventId` as ordering inputs. Equal
  or lower ordering keeps the current projection.
- Store one monotonic TwinVersion per machine. Increment it only when a DataItem projection is
  inserted or replaced; accepted late and duplicate deliveries do not increment it.
- Lock the machine version row with `SELECT ... FOR UPDATE` before reading and changing a Latest
  Observation. This serializes projection changes for one machine while allowing different machines
  to proceed independently.
- Extend the ADR-025 transaction to Inbox, Observation history, machine version, and Latest
  Observation projection. Any projection/version failure rolls back the whole transaction.
- Report `ACCEPTED` when the projection changes, `ACCEPTED_LATE` when only history is stored, and
  `SKIPPED_DUPLICATE` when the Inbox identity already exists.
- `projectedAt` comes from the injected application Clock and remains distinct from source,
  publication, and ingestion times.

## Consequences

- Same-name metrics from different source DataItems do not overwrite each other.
- Concurrent newer/older delivery converges on the newer Observation. The final projection is
  deterministic, while the number of intermediate valid state changes can vary with arrival order.
- The machine version lock is deliberately coarse for the MVP. It favors a simple monotonic version
  contract over maximum write parallelism within one machine.
- Condition projection stores the latest value per source DataItem. Native-code lifecycle and
  Condition-to-Alarm behavior remain separate decisions.

## Verification

- Pure unit tests cover same-session sequence precedence, source-time and source-key ties,
  cross-session comparison, equality, and lower-order rejection.
- `./scripts/verify-database` verifies late history preservation, concurrent convergence, independent
  machine versions, DataItem separation, duplicate idempotency, and full transaction rollback.
