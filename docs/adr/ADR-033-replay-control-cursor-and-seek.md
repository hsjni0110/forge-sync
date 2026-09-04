# ADR-033: Replay Control, Authoritative Cursor, and Seek Rebuild

- Status: Accepted
- Date: 2026-09-04
- Related PRD: 46~50, 119
- Extends: [ADR-023](./ADR-023-deterministic-replay-clock.md),
  [ADR-029](./ADR-029-websocket-twin-patch-resync.md)

## Context

The deterministic Replay domain already preserves source time and controls publication time, but it
had no running HTTP boundary. A browser pause must stop source progression, and a timeline seek must
also move the authoritative 2D/3D Twin. Republishing an older source time into the existing latest
projection would correctly be rejected by the out-of-order guard. Control and projection therefore
need an explicit session handoff rather than weakening ordering.

## Decision

- Replay remains owned by Edge Gateway. Factory API is the browser-facing facade and forwards only
  allowlisted `sourceSetId` commands; no external request supplies a filesystem path.
- A Replay Session exposes a versioned lifecycle state with `revision`. Pause, resume, and speed
  commands include `expectedRevision`; stale commands fail instead of overwriting newer state.
- The Operational Twin v1.2 snapshot and whole-snapshot patch carry one `ReplayCursor` containing
  Replay Session, sequence, source time, replay publication time, and TwinVersion. Cursor and
  Equipment State are committed with the same monotonic TwinVersion.
- Seek prepares a new paused Replay Session, activates that session as the machine's projection
  generation, clears only rebuildable L3 latest/state rows, and then republishes through the last
  Observation at or before the requested source time. It ends paused unless it reaches source end.
- Edge validates the seek target against the current source range before replacing the session, so
  an invalid target cannot clear the active L3 projection. If the start response is lost, Factory
  API reads the authoritative Edge session and retries only while that same session is still
  `PREPARING`.
- Inbox and Canonical Observation history are never cleared. Messages from a non-active Replay
  Session are stored as history but cannot change current Equipment Twin projection.
- `SEEKING`, publication failure, and Edge unavailability are explicit. Presentation freezes
  live-like animation while paused/seeking/failed and uses REST resync after patch gaps.
- The MVP keeps Replay Session runtime in memory. Process restart is observable as unavailable and
  does not claim durable replay recovery. Checkpoint optimization and event markers remain deferred.

## Consequences

- Backward seek does not weaken the general out-of-order policy or overwrite source history.
- A seek can temporarily make the Twin unavailable or partial while L3 is rebuilt. The UI labels
  this state and does not present it as a completed cursor.
- Factory API activation and Edge start are not a distributed transaction. A confirmed Edge start
  is recovered from the authoritative session after response loss. If Edge remains unavailable,
  the projection remains unavailable and a new start can replace the abandoned `PREPARING` session.
- The active-session fence prevents in-flight messages from the previous session contaminating the
  replacement projection.

## Verification

- Fixed-clock Edge tests cover deterministic seek, pause, revision conflict, and supported speeds.
- Shared JSON schemas are consumed by Java and TypeScript contract tests.
- PostgreSQL integration tests cover history preservation, L3 clearing, inactive-session fencing,
  monotonic TwinVersion, and cursor/session convergence.
- Browser tests cover separate time labels, keyboard controls, optimistic rollback, animation
  freeze, and REST/WebSocket convergence.
- Cross-runtime E2E starts the real Replay Edge and verifies browser start through MQTT to the
  authoritative Twin. Failure tests cover invalid seek preservation and ambiguous start recovery.
