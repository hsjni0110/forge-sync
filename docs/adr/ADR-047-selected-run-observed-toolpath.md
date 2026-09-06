# ADR-047: Selected-Run Observed Toolpath

- Status: Accepted
- Date: 2026-09-06

## Context

Step 29 can place the latest X/Y/Z observations in the procedural scene, but a Twin snapshot alone cannot
reconstruct a prior machining path. A path must remain bounded to one selected Machining Run and every displayed
point must be traceable to source observations. The data does not prove material removal, rapid-versus-cutting
motion, OEM kinematics, or physical travel limits.

## Decision

- Add Observed Toolpath contract `1.0.0` at
  `GET /api/v1/machines/{machineId}/observed-toolpath`. Its identity consists of machine, Replay session,
  selected-run start/end sequence, and the authoritative through-sequence watermark.
- Reconstruct held coordinates in deterministic Replay order. Emit a point only when X, Y, and Z are all
  available in millimeters. An unavailable or invalid observation clears that axis until a later valid value.
- Preserve the three contributing source observations on every point. Renderer interpolation is not part of the
  contract and cannot become provenance.
- Deterministically retain points at least 100 ms apart, always retain the final candidate, and cap the response
  at the newest 2,048 points. This is a memory bound, not a Step 47 performance claim.
- Render only the selected/current run using one low-saturation teal line. The optional bounding box is the
  envelope of returned observed points and is not called a machine work envelope.
- Reset the client document before a different selection, session, or Replay watermark is loaded. A failed path
  request does not affect the authoritative 2D Twin.

## Consequences

Operators can inspect one machining interval without accumulating an unbounded whole-Replay trail. The display
states that it connects observed positions and is not a cutting trace, material-removal simulation, collision
guarantee, or machine travel limit. Feed/rapid coloring remains unavailable until a separate alignment policy can
prove how `PATH_FEEDRATE` corresponds to each emitted XYZ point.
