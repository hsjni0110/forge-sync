# ADR-030: Accessible Machine Detail Client Boundary

- Status: Accepted
- Date: 2026-09-02
- Related PRD: 26~27, 63~66, 75, 97, 101, 112~113
- Extends: [ADR-029](./ADR-029-websocket-twin-patch-resync.md)

## Context

The first operational browser route must expose every P0 Twin field and its provenance without
depending on WebGL. It must also distinguish absent data, a missing machine, temporary failure,
reconnection, and stale last-known values. React development StrictMode remounts effects, so a
long-lived session created outside the effect can be disposed and then accidentally reused.

## Decision

- Use React Router with `/machines/:machineId` as the first machine-scoped route. Validate the route
  identity before creating a network session.
- Keep one `TwinLiveSession` as the route's REST/WebSocket state owner. Create it inside the effect,
  dispose it on unmount, and create a fresh instance on every effect mount.
- Decode the versioned Twin contract in the inbound browser Adapter, then map the domain-shaped
  snapshot to a dedicated `MachineDetailViewModel`. The UI does not interpret backend DTO details.
- Reject REST snapshots whose machine identity differs from the requested route and reject
  unparseable contract timestamps or invalid freshness windows.
- Age snapshots with the thresholds carried by the server contract. When local time moves a
  snapshot to STALE, derive STALE consistency and effective connectivity together.
- Preserve every spindle and field-level provenance. Missing or unavailable optional values render
  as `Unavailable`; the client never substitutes zero or guesses a primary spindle.
- Keep last-known values visible during recovery, while showing connection and freshness separately.
  A stale value receives a textual warning and is never presented as live.
- Treat a Twin 404 as terminal by default. When an authoritative Replay session is `RUNNING`, the
  route may treat the missing initial projection as a bootstrap race and request at most five Twin
  resynchronizations with bounded 250 ms to 4 s backoff. This coordination stays outside both the
  Replay and Equipment Twin domain models.
- Render semantic headings, definition lists, status/alert regions, and a keyboard skip link. The 2D
  route has no WebGL or asset dependency.
- Verify the cross-runtime journey with Playwright Chromium against actual PostgreSQL, Mosquitto,
  Factory API, and Factory Web processes. The scenario selects pinned canonical NIST observations,
  verifies their checksum, and controls browser time for the stale boundary.

## Consequences

- Routing and browser E2E add React Router and Playwright as Web dependencies. Playwright's browser
  binary is installed separately from the normal deterministic repository gate.
- A component can render the same ViewModel from REST bootstrap, WebSocket patch, or REST resync
  without separate presentation truth.
- More routes can reuse the shell, but shared state extraction is deferred until another real
  consumer exists.
- `./scripts/verify-e2e` is Docker- and Chromium-dependent and remains separate from
  `./scripts/verify`; both are required when changing this vertical slice.

## Verification

- Component tests cover all P0 sections, provenance, missing/unavailable values, loading, 404,
  retryable failure, reconnecting, and stale warning behavior.
- Session and hook tests cover patch ordering/resync, terminal 404, bounded Replay-driven bootstrap
  retry, explicit retry, and StrictMode lifecycle isolation.
- `./scripts/verify-e2e` covers replay-driven RPM changes, browser disconnect and STALE behavior,
  offline update, REST recovery to the authoritative version, resubscription, keyboard focus, and
  semantic section labels.
