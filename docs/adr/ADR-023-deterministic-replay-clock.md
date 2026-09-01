# ADR-023: Deterministic Replay Clock and Publication Boundary

- Status: Accepted
- Date: 2026-09-01

## Context

Observation v2 preserves historical `sourceObservedAt` and provides a separate optional Replay
identity. Replay must support pause, resume, and 1x/10x/100x speed while retaining a reproducible
event order. MQTT delivery belongs to the following implementation stage and has at-least-once
failure behavior, so advancing replay before a successful publisher handoff would lose position.

## Decision

- Replay reads the versioned serialized Observation contract through a Replay-owned port. It does
  not import Ingestion entities.
- Historical observations are ordered by `sourceObservedAt`, then `sourceEventKey`. The first item
  is immediately due and receives zero-based `replaySequence`.
- `ReplayClock` scales each adjacent source interval by 1, 10, or 100 and rounds up to one
  microsecond. Pause freezes the remaining delay; resume anchors that delay to the injected wall
  Clock. A speed change rescales only the remaining delay.
- The Replay adapter adds the complete Replay identity group without changing source identity,
  provenance, event identity, or payload.
- Replay position and sequence advance only after the `ReplayPublisher` returns successfully. A
  publisher failure leaves the same Observation available for retry.
- This stage uses a serial in-memory session. Persistence, concurrency, MQTT, real-time sleeping,
  seek, checkpoints, and MAX speed remain outside this decision.

## Consequences

- Fixed Clock and ID generators make lifecycle and timing tests deterministic.
- The same Canonical artifact produces the same sequence hash at every supported speed because the
  hash covers only `replaySequence` and `sourceEventKey`.
- A process restart loses in-memory session position. Durable recovery is intentionally deferred to
  the delivery stage, where transport failure behavior can be handled end to end.
