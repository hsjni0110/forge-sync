# ADR-031: Isolated React Three Fiber Scene

- Status: Accepted
- Date: 2026-09-03
- Related PRD: 29~33, 102~103, 110, 121
- Extends: [ADR-030](./ADR-030-accessible-machine-detail-client.md)

## Context

The first spatial route needs a real browser-rendered scene without making WebGL, a model file, or
the renderer bundle a prerequisite for operational Twin data. ForgeSync has no verified external
CNC GLB or repository-wide license declaration yet. Treating an unverified model as the default
would weaken provenance and redistribution claims, while loading the renderer in the common bundle
would unnecessarily couple the established 2D route to Three.js.

## Decision

- Add `/factory` with accessible `2D`, `3D`, and `SPLIT` modes. `SPLIT` is the default, and the
  existing Mazak01 Machine Detail remains the authoritative operational panel.
- Load the React Three Fiber v9 scene through `React.lazy`. The normal application shell and 2D
  Machine Detail do not import Three.js or create a WebGL context.
- Put a DOM error boundary around the lazy scene. A rejected bundle or scene render exception
  produces a 3D-unavailable notice and makes the 2D panel visible even from 3D-only mode.
- Treat the React Three Fiber `Canvas` fallback only as HTML canvas fallback content; mounting that
  content is not a WebGL capability signal. Probe a WebGL2 context with the renderer's requested
  attributes before mounting Canvas and report failure through the scene availability boundary.
  Asset errors have a narrower boundary inside the Canvas and replace only the failed model with a
  generic primitive.
- Prefer the default GPU in the WebGL renderer and cap device pixel ratio at 1.5. This avoids
  unnecessarily requesting a high-performance GPU and bounds Retina rendering cost without making
  an unverified Safari performance claim.
- Distinguish a WebGL initialization failure from a bundle/scene failure in operator-facing
  guidance, and let the operator recreate the lazy scene and Canvas with an explicit retry.
- Version the factory asset manifest separately from Twin contracts. Every entry records identity,
  origin, representation, attribution, and license evidence; GLB entries additionally require URI,
  byte length, and SHA-256.
- Fetch a declared GLB as bytes and verify its byte length and SHA-256 before GLTF parsing. A missing
  or changed asset therefore follows the generic primitive fallback instead of silently rendering.
- Ship no external binary asset in this decision. The default model is procedural geometry and its
  undeclared repository license is recorded as `NOASSERTION`/`TO_VERIFY`, not silently upgraded to a
  redistribution claim.
- Label the initial layout `SIMULATED_LAYOUT`. The scene neither reads backend Twin DTOs nor owns or
  changes equipment state; those bindings remain later work.

## Consequences

- React Three Fiber and Three.js are production dependencies, but Vite emits them in the lazy
  Factory Scene chunk instead of the common 2D chunk.
- Three failure levels have different outcomes: asset failure keeps the scene with a primitive;
  WebGL capability, bundle, or scene failure removes the scene but preserves the 2D panel.
- The initial scene is intentionally static. Selection, `MachineVisualState`, RPM/state animation,
  camera focus, and reduced-motion behavior are not implied by this ADR.
- `SPLIT` renders a compact operational summary rather than squeezing the complete 2D detail into a
  narrow column. `2D` renders the complete detail directly. Full provenance is collapsed by default
  so long immutable identities do not obscure current values.
- An external CNC model cannot become the default merely by adding a file. Its manifest must satisfy
  the contract and its redistribution evidence must be verified in the Verification Ledger.

## Verification

- Contract tests accept the procedural entry and reject an incompatible manifest or an incomplete
  GLB identity/provenance record.
- Component tests cover all view modes, rejected lazy imports, WebGL2 capability failure, the
  Canvas-fallback false-positive regression, GLB integrity, and 404 or invalid GLB fallback.
- `./scripts/verify-e2e` opens the normal Canvas in Chromium, then denies WebGL context creation and
  confirms that the current 2D Twin value remains usable. The normal path waits for the first scene
  frame instead of treating canvas DOM insertion as renderer readiness.
