# ADR-054: Observed Metric Channel Disclosure in the Twin Snapshot

- Status: Accepted
- Date: 2026-09-11
- Related PRD: 17, 23, 26, 27, 63~66, 112~113
- Extends: [ADR-028](./ADR-028-versioned-operational-twin-snapshot.md),
  [ADR-029](./ADR-029-websocket-twin-patch-resync.md)

## Context

Canonical mapping `2.2.0` carries observed channels that no consumer can read: `LOAD` 14,473,
`TEMPERATURE` 10,203, `PATH_FEEDRATE` 7,633 records plus `PART_COUNT`, `CONTROLLER_MODE`, and
`POWER_STATE` events. They are stored, ordered, and provenance-tracked, but the authoritative Twin
snapshot stops at spindle speed, X/Y/Z position, B-axis angle, tool, and program, so an operator
cannot see a load or a temperature at all.

Two properties of the source shape the contract. `LOAD` and `TEMPERATURE` each arrive from more
than one component (`Mazak01-B`/`-C`/`-C2`/`-X`/`-Y`/`-Z` and `Mazak01-C`/`-C2`), and the source
never says which component is the primary one or which of them is an axis rather than a spindle.
The existing P0 set also decides `consistency.status`, so treating the new channels as P0 would
turn most snapshots `PARTIAL` for channels the source samples only occasionally.

## Decision

- Raise the snapshot and patch contract to `1.6.0` and add `metrics.loads`, `metrics.temperatures`,
  `metrics.pathFeedrate`, `metrics.partCount`, `metrics.controllerMode`, and `metrics.powerState`.
  The patch envelope keeps carrying one whole snapshot, so both versions move together.
- Return multi-component channels as collections ordered by `componentId` then `sourceDataItemId`,
  each entry keeping its own observation time and field-level provenance. Do not classify a channel
  as an axis or a spindle load, and do not elect a primary component.
- Bind each channel to the unit its catalog declares: `PERCENT` for load, `CELSIUS` for temperature,
  `MILLIMETER/SECOND` for path feedrate. A document carrying any other unit on those channels is
  invalid rather than converted.
- Accept a single-component channel only when exactly one matching observation exists with the
  expected unit. Ambiguity leaves the field absent instead of choosing one.
- Keep the new fields optional and outside `consistency.missingFields`. Their absence is not a
  missing P0 value, so `CONSISTENT` keeps its existing meaning and a consumer written against
  `1.5.0` semantics renders a `1.6.0` snapshot unchanged.
- An unavailable observation keeps its identity and provenance and omits `value` and `unit`. An
  observed zero stays zero; only a real observation produces a number.
- `PART_COUNT` stays a telemetry metric. `productionResult` remains an empty container, and the 2D
  client labels the value as a controller counter rather than a production result.

## Consequences

- A single snapshot now answers position, load, feedrate, temperature, part count, controller mode,
  and power state with the provenance of each, which is what Step 38's shift overview needs.
- One patch carries more bytes. A contract test caps a fully populated snapshot at 64 KiB; Step 47
  measures the achieved publish rate and narrows that ceiling with its measurement conditions.
- Consumers that want an axis-versus-spindle grouping must define that mapping explicitly, the same
  way `metrics.axisPositions` names its X/Y/Z DataItems.
- Deferred per-axis feedrate and C-axis DataItems stay unmapped; this ADR exposes only what the
  canonical mapping already carries.

## Verification

- Shared-schema contract tests validate the golden Twin fixture, the REST mapper output, channel
  separation, rejected units, and a snapshot without the new optional fields.
- Application tests cover component ordering, unavailable channels, an ambiguous feedrate, and the
  absence of new entries in `missingFields`.
- Frontend contract, view-model, and component tests verify the rendered channels, their source
  DataItems, an observed zero, and an unchanged render when the new fields are absent.
