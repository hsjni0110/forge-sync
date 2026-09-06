# ADR-048: Functional Twin Replay Composition

- Status: Accepted
- Date: 2026-09-06

## Context

Execution, spindle RPM, XYZ observations, active tool, Machining Run, and an observed path are produced by
different presentation adapters. Showing them independently can make a previously selected historical path look
like the machining run at the current Replay cursor. A running-looking animation must also stop when Replay is
paused or data is stale, even if the last observed execution and RPM still say ACTIVE and non-zero.

## Decision

- Compose the compact functional presentation from one authoritative Machine Visual State and its Replay cursor.
- Keep the run containing the current cursor separate from an explicitly selected historical path. A selected
  path is shown only when its Replay session and sequence bounds match the loaded Observed Toolpath document.
- Label direct Twin values as `OBSERVED`, run segmentation as `DERIVED`, and reconstructed paths as
  `OBSERVED_PATH`. B/C coordinates and tool geometry remain unavailable when no validated mapping or registry
  exists.
- Allow live motion only for online, fresh, ACTIVE, positive-RPM, advancing Replay state. STOPPED, pause, stale,
  and disconnected states do not advance the visual motion.
- Keep detailed provenance collapsed in model information while exposing a low-contrast, always-visible current
  action summary. The replay behavior fixture is explicitly `SIMULATED_TEST_FIXTURE` and is not product data.

## Consequences

An operator can compare a historical path without losing sight of what the Replay cursor currently represents.
The 3D summary is easier to scan and cannot promote a selected historical run, unsupported axis, or authored tool
shape into an observed machine fact. The composition remains renderer-side and changes no REST, WebSocket, or
canonical contract.
