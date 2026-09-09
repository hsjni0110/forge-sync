# Twin Contracts

The authoritative Operational Twin snapshot is exposed by:

```text
GET /api/v1/machines/{machineId}/twin
Accept: application/vnd.forgesync.twin.v1+json
```

Version `1.5.0` is defined by
[`v1/twin-snapshot.schema.json`](./v1/twin-snapshot.schema.json). The schema keeps the PRD 27
sections stable while the MVP populates machine identity, consistency, Replay Cursor, Equipment State, freshness,
spindle speeds, X/Y/Z axis positions, tool, program, current Conditions, and field-level provenance. Unsupported business
sections remain typed empty containers; they do not imply simulated or inferred facts.

## RPM and unavailable values

Mazak01 exposes more than one spindle DataItem. `metrics.spindleSpeeds` therefore returns every
current `SPINDLE_SPEED`, ordered by `componentId` and `sourceDataItemId`; this contract does not guess
a primary spindle. An unavailable observation keeps its identity and provenance but omits `value`
and `unit`. Missing or unavailable P0 fields are listed in `consistency.missingFields` and never
become zero or an empty string.

`metrics.axisPositions` exposes only unambiguous `POSITION` observations from the configured X, Y,
and Z DataItems in `MILLIMETER`. Each axis retains its own observation time and provenance. These
numbers are observed machine-coordinate values; the contract does not define physical travel limits,
an absolute 3D pose, or a scene scale.

The required `replayCursor` is committed with the projection and its `twinVersion` must equal the
snapshot consistency version. It keeps `sourceObservedAt` separate from `replayPublishedAt`.

The freshness object carries the applied `freshMaxAgeMillis` and `laggingMaxAgeMillis`. Browser
consumers use these server-owned thresholds when aging a snapshot between updates; they do not
redefine the policy locally.

## Consistency and errors

- `CONSISTENT`: every P0 field is currently available.
- `PARTIAL`: at least one P0 field is missing, unavailable, or ambiguous.
- `STALE`: freshness is older than the configured lagging threshold; this takes precedence over
  `PARTIAL`.
- `DEGRADED`: reserved for a later explicitly defined source; v1 does not currently emit it.

The reader uses one repeatable-read transaction. A missing machine returns `404` with
`MACHINE_TWIN_NOT_FOUND`. A missing Equipment State, a mismatched state/Twin version, or a failed
projection query returns `503` with `TWIN_SNAPSHOT_UNAVAILABLE`; no mixed-version snapshot is
returned. Problem responses use `application/problem+json`.
`spatial` is optional and atomic. Position uses non-physical `SCENE_UNIT`, rotation uses
`RADIAN`, scale is a positive dimensionless multiplier, and current layouts retain
`SIMULATED_LAYOUT` provenance.

## Range-scoped utilization

Utilization is exposed separately from the current Twin snapshot:

```text
POST /api/v1/machines/{machineId}/utilization-kpis/processing-runs
GET  /api/v1/machines/{machineId}/utilization-kpis/processing-runs/{processingRunId}
Accept: application/vnd.forgesync.utilization-kpis.v1+json
```

The POST body names an immutable Equipment State interval processing run and calculation version.
[`v1/utilization-kpis.schema.json`](./v1/utilization-kpis.schema.json) returns state dwell ratios over
the complete observed range and machine-counter ratios as separate evidence paths. Unknown and
uncovered time remain explicit. Counter resets and unavailable observations produce partial
coverage; invalid counter relationships produce an unavailable value rather than a clamped ratio.
The contract does not claim planned-time Availability, Quality, or composite OEE. See
[ADR-051](../../docs/adr/ADR-051-observed-utilization-kpi-projection.md).

## Downtime Pareto

Ranked downtime is exposed as another immutable projection:

```text
POST /api/v1/machines/{machineId}/downtime-pareto/processing-runs
GET  /api/v1/machines/{machineId}/downtime-pareto/processing-runs/{processingRunId}
Accept: application/vnd.forgesync.downtime-pareto.v1+json
```

The POST body names an immutable Utilization processing run and rule version.
[`v1/downtime-pareto.schema.json`](./v1/downtime-pareto.schema.json) ranks closed
STOPPED/INTERRUPTED/UNKNOWN intervals and keeps their boundary observations. Emergency-stop
interval overlap, mode-change observations, and point-in-time CONDITION WARNING/FAULT observations
are returned as concurrent evidence only. `UNCONFIRMED_REASON` stays explicit when no evidence
overlaps; the contract never promotes temporal overlap to a cause. See
[ADR-052](../../docs/adr/ADR-052-downtime-pareto-concurrent-evidence.md).
