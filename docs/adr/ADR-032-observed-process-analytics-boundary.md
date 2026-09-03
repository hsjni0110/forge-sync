# ADR-032: Observed Process Analytics Boundary

- Status: Accepted
- Date: 2026-09-03
- Related PRD: 6~7, 63~66, 120
- Extends: [ADR-028](./ADR-028-versioned-operational-twin-snapshot.md)
- Related guides:
  [NIST Mazak + PHM 2010](../ForgeSync_NIST_Mazak_PHM2010_수정가이드.md),
  [Three.js Procedural 3D](../ForgeSync_ThreeJS_Procedural_3D_수정가이드.md)

## Context

The accepted NIST observation pipeline, Operational Twin, and 2D/3D projections reconstruct the
current equipment state but do not own a machining-cycle lifecycle. Adding `MachiningRun`,
`CycleFeature`, and `AnomalyAssessment` inside Equipment Twin would couple rebuildable historical
analysis to authoritative current state. Putting them in Production would confuse an observed
manufacturing interval with a user-created `OperationExecution`; putting them in Intelligence would
mix deterministic process reconstruction with separately sourced model inference.

## Decision

- Add a Process Analytics Bounded Context. It owns `MachiningRun`, `CycleFeature`,
  `AnomalyAssessment`, segmentation rules, and their processing versions.
- Accept only validated Canonical Observations at the Process Analytics boundary. A pure domain
  policy classifies resulting process facts as `DERIVED` and rejects `SIMULATED_OPERATION` and
  `REFERENCE_HEALTH` inputs.
- Preserve the nested observation source as `REAL:NIST`; `DERIVED` describes the transformation and
  does not replace source provenance. Do not introduce `OBSERVED` as a new wire provenance value.
- Define Process Analytics application ports from the consumer's perspective. Domain and
  application code do not import Ingestion, Equipment Twin, Production, or Intelligence internals
  and never read their repositories.
- End the Ingestion/Equipment Twin transaction before Process Analytics begins. Process results are
  independently rebuildable under their own processing transaction and never extend the Inbox
  effectively-once boundary.
- Base process intervals on `sourceObservedAt`. Preserve replay, ingestion, and projection times for
  their existing meanings; none becomes the machining event time.
- Define a future `MachiningRunId` deterministically from `processingRunId`, `machineId`,
  `segmentationRuleVersion`, and the start anchor's `sourceEventKey`. It is not an
  `OperationExecution` identity and is not stable across a new Processing Run.
- Keep public JSON, REST, event, and persistence contracts out of this decision. Introduce them with
  the producing and consuming behavior in the Machining Run segmentation slice.
- Keep the current Twin v1 and `MachineVisualState` contracts unchanged. Future Process Analytics
  results reach Presentation through a versioned query contract rather than being inferred in the
  renderer.

## Consequences

- Process reconstruction can be reprocessed without rewriting Canonical Observations or rolling
  back the current Equipment Twin.
- Production, reference health, and observed process analysis retain separate identities,
  lifecycles, provenance, and transaction boundaries.
- The first Process Analytics code is deliberately only a semantic source policy and package
  boundary. There is no empty Repository, Controller, table, or speculative cross-runtime DTO.
- Step 18 must define segmentation behavior and introduce its contract with a producer and consumer
  test in the same vertical slice.

## Verification

- Domain tests accept Canonical Observation input as `DERIVED` and reject simulated operation,
  reference health, and missing source classifications.
- ArchUnit rejects an intentional Process Analytics application dependency on an Equipment Twin
  Adapter and continues to enforce inward domain/application dependencies.
- The Mazak01 regression manifest pins checked-in Twin, patch, and E2E scenario bytes plus the
  established Twin/visual meaning from the completed baseline.
- `./scripts/verify`, `./scripts/verify-database`, and `./scripts/verify-e2e` verify that this boundary
  decision does not change established ingestion, Twin, or Presentation behavior.
