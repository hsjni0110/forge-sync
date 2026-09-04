# Twin Contracts

The authoritative Operational Twin snapshot is exposed by:

```text
GET /api/v1/machines/{machineId}/twin
Accept: application/vnd.forgesync.twin.v1+json
```

Version `1.2.0` is defined by
[`v1/twin-snapshot.schema.json`](./v1/twin-snapshot.schema.json). The schema keeps the PRD 27
sections stable while the MVP populates machine identity, consistency, Replay Cursor, Equipment State, freshness,
spindle speeds, tool, program, current Conditions, and field-level provenance. Unsupported business
sections remain typed empty containers; they do not imply simulated or inferred facts.

## RPM and unavailable values

Mazak01 exposes more than one spindle DataItem. `metrics.spindleSpeeds` therefore returns every
current `SPINDLE_SPEED`, ordered by `componentId` and `sourceDataItemId`; this contract does not guess
a primary spindle. An unavailable observation keeps its identity and provenance but omits `value`
and `unit`. Missing or unavailable P0 fields are listed in `consistency.missingFields` and never
become zero or an empty string.

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
