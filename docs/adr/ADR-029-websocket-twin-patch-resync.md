# ADR-029: Whole-snapshot WebSocket Patch and REST Resynchronization

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 63~65, 101
- Extends: [ADR-028](./ADR-028-versioned-operational-twin-snapshot.md)

## Context

WebSocket delivery can be duplicated, reordered, or lost. A field-only patch would also need
deletion, provenance, freshness, and partial-consistency rules that can leave 2D and 3D consumers
on different versions if only part of a message is applied.

## Decision

- Emit a versioned `TWIN_PATCH` containing `baseVersion`, `targetVersion`, and one complete v1 Twin
  snapshot after a projection transaction commits.
- Apply the snapshot atomically only for the next local version. Ignore duplicate or regressive
  targets and perform REST resynchronization for gaps, invalid messages, and reconnects.
- Bootstrap and recover in the order REST snapshot, local version replacement, then WebSocket
  subscription. Messages received while resynchronizing are not applied.
- Treat WebSocket delivery as best-effort notification. Publication failure is observable but does
  not roll back ingestion or change MQTT acknowledgment after the database commit.
- Use a machine-scoped raw WebSocket endpoint and an explicit origin allowlist. REST remains the
  authoritative, human-readable query contract.

## Consequences

- The first implementation sends more bytes than a field delta but cannot leave provenance or
  consistency fields partially updated.
- Concurrent notifications may expose a version gap; this is safe because the client resynchronizes
  instead of guessing the missing state.
- A future field-delta format requires a new compatible contract decision and measurements showing
  that whole-snapshot traffic is a material constraint.

## Verification

- Shared schema fixtures are validated by the Java producer and TypeScript consumer.
- Server integration tests cover machine-scoped delivery and non-fatal publication failure.
- Frontend fake-clock tests cover continuous, duplicate, regressive, gap, invalid, disconnect, stale,
  resync, and resubscribe behavior.
- `./scripts/verify-e2e`에서 실제 Chromium의 offline 전환, STALE 경계, offline 중 새 Observation,
  online 복구 뒤 REST snapshot version/value 수렴과 WebSocket 재구독을 검증한다.
