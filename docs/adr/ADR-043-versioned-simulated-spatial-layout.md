# ADR-043 — Versioned Simulated Spatial Layout

## Status

Accepted — 2026-09-06

## Context

The Operational Twin exposed an empty `spatial` object while the web renderer owned Mazak01's
asset, position, rotation, scale, and simulated provenance as a constant. This made renderer code,
rather than data, authoritative for placement.

## Decision

- Machine placement is versioned in `config/spatial/machine-layout-v1.json` and packaged into the
  API runtime. It is configuration, not an observed NIST fact or database projection.
- Twin snapshot `1.4.0` optionally exposes one complete spatial layout: asset and scene-node
  identities, three-component position/rotation/scale vectors, and provenance.
- Position uses `SCENE_UNIT`, rotation uses `RADIAN`, and scale is a positive dimensionless
  multiplier. `SCENE_UNIT` must not be described as metres or another physical unit.
- Every configured layout is `SIMULATED_LAYOUT`. Invalid or incomplete machine entries are
  unavailable atomically; valid entries for other machines remain usable.
- The frontend contract decoder validates the wire value. A Twin adapter copies it into
  `MachineVisualState`; renderer code does not import or interpret backend DTOs.
- Missing spatial data or an unknown asset uses the complete existing procedural fallback and
  visibly reports `배치 정보 사용 불가 · 기본 배치`.

## Consequences

Changing the versioned configuration moves a machine without renderer code changes. Spatial
metadata is authoritative for this simulated scene only and does not assert the physical NIST
factory layout. REST and WebSocket retain their v1 media types while schema version advances.
