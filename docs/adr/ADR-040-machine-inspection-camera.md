# ADR-040: Machine Inspection Camera and Accessible Controls

- Status: Accepted
- Date: 2026-09-05
- Extends: [ADR-031](./ADR-031-isolated-react-three-fiber-scene.md),
  [ADR-039](./ADR-039-machine-model-node-contract.md)

## Context

The Step 22 model has stable functional references, but the fixed camera leaves the machine small
and does not let an operator inspect its chuck, workpiece, milling head, or mounts. Canvas-only
mouse controls would also exclude keyboard and screen-reader users. Making the enclosure disappear
entirely would remove useful spatial context, while treating a procedural shape as physical Mazak
geometry would overstate its provenance.

## Decision

- Use Three.js `OrbitControls` without a new dependency. Mouse/touch gestures and an accessible DOM
  toolbar share the same camera commands. A focused viewport maps arrows, plus/minus, Home, and
  Escape to rotate, zoom, reset, and part-selection clearing.
- Calculate full-model and part focus from their `Box3`. The default three-quarter view targets
  roughly 70 percent viewport occupancy. Distance is bounded to 0.45–4 times the model radius and
  polar rotation to 20–85 degrees.
- Focus transitions last 250 ms. Reduced-motion preference makes them immediate and disables orbit
  damping.
- Extend the renderer-internal model contract with six labeled inspection references and an
  optional enclosure capability. Runtime interaction uses these references and does not search by
  semantic name. Missing enclosure capability disables only transparency; it does not invalidate an
  otherwise conforming GLB.
- Hover and selection use non-authoritative bounding helpers. Selection shows one visual label and
  an equivalent DOM status. Workpiece remains `대표 공작물 · SIMULATED` and layout remains
  `SIMULATED_LAYOUT`.
- Procedural enclosure transparency uses opacity 0.2 with depth writing disabled. Original GLB
  materials are cloned before inspection changes and their exact state is restored.

## Consequences and limits

- Camera and inspection state remain inside the lazy renderer and do not change Twin, Replay, REST,
  or WebSocket contracts. WebGL failure still preserves the authoritative 2D view.
- B-axis observation, tool registry, tool change, cutting, doors, and physical coordinate claims
  remain outside this decision. Camera navigation completes that portion of Step 25 early without
  claiming the rest of Step 25 complete.

## Verification

- Pure framing tests cover proportional bounds and portrait aspect ratios.
- Component tests cover equivalent button/keyboard commands, part focus, simulated workpiece label,
  transparency, restoration, reduced-motion inputs, and unsupported GLB enclosure capability.
- Browser acceptance operates the controls in a real WebGL scene and then retains the existing
  WebGL-failure/2D authority scenarios.
