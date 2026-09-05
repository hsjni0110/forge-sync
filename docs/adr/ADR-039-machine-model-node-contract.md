# ADR-039: Machine Model Node Contract and Procedural Skeleton

- Status: Accepted
- Date: 2026-09-05
- Related: [ADR-031](./ADR-031-isolated-react-three-fiber-scene.md)

## Context

The Factory renderer previously created procedural geometry and interpreted runtime Twin state in
the same React component. The visual spindle and status beacon were separate scene objects rather
than functional parts of the model. A future GLB could therefore load successfully while omitting
the nodes required by runtime behavior, leaving a partial and misleading machine in the scene.

The project has not selected or licensed a default external GLB. The current shape is a project
procedural representation, not a claim that it reproduces the physical Mazak01 machine. Its
representative stock and factory position also need explicit simulated provenance.

## Decision

- Every machine provider returns one `MachineTwinModel`: a detached root plus direct references to
  `main-spindle`, `main-chuck`, `b-axis-pivot`, `milling-head`, `tool-mount`, and `workpiece-mount`.
- Stable names and the following hierarchy form the renderer-internal contract:

  ```text
  machine-root
  ├── static-body
  ├── main-spindle-group
  │   ├── main-spindle
  │   ├── main-chuck
  │   └── workpiece-mount
  └── b-axis-pivot
      └── milling-head
          └── tool-spindle
              └── tool-mount
  ```

- The procedural factory creates geometry only. It does not consume `MachineVisualState`, backend
  DTOs, or stores. Runtime binding consumes a validated model and `MachineVisualPresentation`; it
  updates explicit references and never creates geometry or searches by name.
- A GLB provider clones a verified asset, resolves each semantic node once, and validates unique
  names and parent relationships before the root is attached to the scene. Missing, duplicate, or
  misplaced nodes reject the whole model. The existing asset error boundary then mounts one fresh
  procedural model and reports the fallback once.
- The procedural representative workpiece is `SIMULATED`; the existing scene placement remains
  `SIMULATED_LAYOUT`. Both labels are exposed in the scene explanation. The procedural source
  locator points to its factory implementation.

## Consequences and limits

- Procedural and future GLB representations share runtime binding behavior without changing REST,
  WebSocket, or Twin schemas.
- A malformed GLB cannot be partially presented, but an asset author must conform to the semantic
  names and hierarchy before the model is usable.
- No external asset, dependency, physical coordinate transform, B-axis observation binding, tool
  registry, tool change behavior, or product CAD is introduced. Those physical behaviors remain
  later roadmap decisions.
- Spindle rotation remains the existing non-physical visual cue. ACTIVE/STOPPED/STALE,
  reduced-motion, selection, and accessible status semantics are unchanged.

## Verification

- Contract tests inspect exact references, semantic names, hierarchy, geometry landmarks, and
  simulated workpiece provenance.
- Provider/component tests reject incomplete GLBs and verify one atomic procedural fallback.
- Binding tests preserve root identity while checking active rotation and stale freeze/material
  muting. The architecture check prevents state imports in the factory and geometry construction or
  semantic name lookup in the binding.
