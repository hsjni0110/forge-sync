# ADR-025: PostgreSQL Ingestion Transaction Boundary

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 97~100
- Extended by: [ADR-026](./ADR-026-latest-observation-ordering.md)

## Context

MQTT QoS1 can deliver the same valid Observation more than once. Broker acknowledgment must occur
only after durable application handoff, while duplicate delivery must not create duplicate business
acceptance. The current implementation validates Observation v2 at the MQTT boundary but has no
database transaction, Inbox, or Canonical Observation history.

The product architecture selects PostgreSQL with TimescaleDB. Inbox identity needs a global unique
constraint, and the current step does not yet define time partitioning, retention, or compression
requirements for Observation history.

## Decision

- Use `(replay_session_id, source_event_key)` as the Inbox primary key and as a foreign-key identity
  and primary key for Canonical Observation history. `event_id` remains a queryable Canonical
  identity but is not globally unique across Replay Sessions.
- An application use case injects `ingestedAt` from a `Clock` and delegates to one atomic persistence
  port. Its PostgreSQL adapter owns the framework transaction.
- The adapter claims the Inbox with `INSERT ... ON CONFLICT DO NOTHING`. A successful claim inserts
  exactly one history row in the same transaction. A failed history insert rolls back the Inbox
  claim.
- Return `ACCEPTED` for the committed path and `SKIPPED_DUPLICATE` for an existing Inbox identity.
  Invalid schema or transport input remains an inbound rejection and never enters this transaction.
- Preserve queryable identity, category, ordering, time, and provenance columns together with the
  complete validated Canonical envelope as JSONB.
- Run regular PostgreSQL tables on the pinned PostgreSQL + TimescaleDB image. Do not create a
  hypertable until partitioning and retention requirements are decided and tested.
- At this stage the declared boundary covers Inbox and Observation history. ADR-026 extends it with
  Latest Observation projection and machine TwinVersion; raw telemetry does not create Outbox
  records.

## Consequences

- Concurrent duplicates serialize at the database unique constraint and produce one business
  acceptance without claiming global exactly-once delivery.
- MQTT duplicates remain observable and are acknowledged after either `ACCEPTED` or
  `SKIPPED_DUPLICATE`; database failure remains unacknowledged.
- The full envelope and explicit provenance references remain traceable without using persistence
  rows as Domain models.
- Timescale-specific optimization is deferred, avoiding a premature partition key that could weaken
  the required global Inbox identity.

## Verification

- Unit tests use a fixed Clock and distinguish accepted, duplicate, invalid, and storage-failure
  behavior.
- PostgreSQL integration tests inject 100 concurrent copies, force a history constraint failure to
  verify rollback, and preserve SAMPLE/EVENT/CONDITION time and provenance.
- `./scripts/verify-database` runs the database integration suite against the pinned image.
