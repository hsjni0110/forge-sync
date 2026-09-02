# ADR-028: Versioned Operational Twin Snapshot Query

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 17, 23, 26, 27, 63~66, 75, 112~113
- Extends: [ADR-027](./ADR-027-equipment-state-and-freshness.md)

## Context

Equipment State, Latest Observations, and TwinVersion are persisted atomically, but REST, 2D, and
3D consumers need one versioned, human-readable query contract. The selected Mazak01 source has two
mapped spindle-speed DataItems, so flattening them to one RPM would invent a primary spindle. Values
may also be unavailable while their observation identity and provenance remain meaningful.

## Decision

- Expose `GET /api/v1/machines/{machineId}/twin` with media type
  `application/vnd.forgesync.twin.v1+json` and schema version `1.0.0`.
- Read machine version, Equipment State, and Latest Observations in one PostgreSQL repeatable-read
  transaction through an outbound Application Port. The REST Adapter never reads JDBC state.
- Reject a missing Equipment State or a state version different from the current machine
  TwinVersion. Do not return a mixed-version snapshot.
- Compute Freshness at query time with the injected Clock and ADR-027 policy. `STALE` consistency
  takes precedence over `PARTIAL`; otherwise missing, unavailable, or ambiguous P0 values are
  `PARTIAL`.
- Return all spindle speeds ordered by component and source DataItem identity. Do not select a
  primary spindle without an explicit machine mapping decision.
- Keep value-level Canonical provenance and observation/source/projection times on RPM, tool,
  program, and Condition fields. Derived state exposes the provenance of observations used by its
  policy. An unavailable value omits the value instead of substituting a default.
- Keep PRD 27 sections present. Contexts not implemented in this slice use typed empty containers
  and make no production, Alarm, Maintenance, Intelligence, quality, or spatial claims.
- Return stable `application/problem+json` codes for invalid identity, missing machine, and
  unavailable snapshot. Do not expose database errors, raw payloads, or stack traces.

## Consequences

- REST resync and later 2D/3D adapters can share one authoritative TwinVersion and freshness basis.
- Consumers must handle a spindle-speed collection and explicitly choose a visual spindle in a
  later machine mapping contract.
- Adding fields to reserved sections requires a compatible contract revision and its own Context
  query; the Equipment Twin Adapter does not reach into other Context repositories.
- Repeatable-read protects one snapshot from concurrent projection changes without changing the
  ingestion transaction or database schema.

## Verification

- Contract tests validate the shared schema, golden fixture, version, provenance, and unavailable
  value rules.
- Application tests use a fixed Clock for consistent, partial, and stale snapshots, two-spindle
  ordering, missing machine, and mismatched versions.
- HTTP tests verify 200/400/404/503 behavior and stable problem codes.
- `./scripts/verify-database` reads the typed snapshot from actual PostgreSQL after Sample, Event,
  and Condition projection and verifies version and provenance preservation.
