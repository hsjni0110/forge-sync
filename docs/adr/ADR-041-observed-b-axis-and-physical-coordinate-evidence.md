# ADR-041: Observed B-axis and Physical Coordinate Evidence

- Status: Accepted
- Date: 2026-09-06
- Extends: [ADR-028](./ADR-028-versioned-operational-twin-snapshot.md),
  [ADR-039](./ADR-039-machine-model-node-contract.md)

## Context

The NIST Mazak01 profile identifies `Mazak01-B_4` (`Bdeg`) as an actual `ANGLE` Sample in
`DEGREE`. It does not establish the physical Three.js rotation axis, sign, zero offset, or machine
travel limits. Treating an observed value as a verified coordinate transform would therefore make
an unsupported physical claim.

## Decision

- Canonical mapping `2.1.0` maps only `Mazak01-B_4` to `ANGLE/DEGREE`; B travel Conditions and C-axis
  observations remain separate.
- Operational Twin `1.3.0` exposes one optional `metrics.bAxisAngle` with observation identity and
  field provenance. Missing, duplicate, unavailable, or non-degree candidates are not replaced by
  a default and make the field missing.
- A renderer coordinate mapping is immutable and declares source DataItem, unit, rotation axis,
  sign, zero, limits, and an evidence reference. Conversion rejects unknown units and values outside
  the declared limits rather than clamping them.
- Mazak01 has no coordinate mapping until equivalent physical evidence is recorded. Its observed
  angle is visible in 2D, while 3D explicitly reports `좌표 매핑 검증 전 · unavailable` and leaves the
  B-axis pivot at its model-authored orientation.

## Consequences

- Observation provenance can be inspected without overstating the procedural model's physical
  accuracy. A verified mapping can later activate the existing explicit pivot binding.
- Twin and WebSocket schema versions advance together to `1.3.0`; endpoint and media type remain v1.
- C-axis, linear axes, tool geometry, and machine travel-limit inference remain out of scope.

## Verification

- Canonical contract tests cover Bdeg type, unit, source DataItem, locator, and mapping version.
- Twin tests cover unique selection and missing, duplicate, unavailable, and wrong-unit behavior.
- Pure coordinate tests cover zero, signed boundaries, degree-to-radian conversion, and rejection.
- UI tests cover the 2D observed value/provenance and explicit unverified 3D status.
