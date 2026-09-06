# ADR-046: Observed Linear-Axis Delta Visualization

- Status: Accepted
- Date: 2026-09-06

## Context

The pinned Mazak01 source identifies `Mazak01-X_1`, `Mazak01-Y_1`, and `Mazak01-Z_1` as machine-coordinate
actual positions in millimeters. The canonical mapping already preserves these observations. It does not,
however, establish the physical model origin, scene-axis orientation, authored-model scale, or machine travel
limits. The `Xtravel`, `Ytravel`, and `Ztravel` DataItems are Conditions rather than numeric limit metadata.

Showing no motion makes replay harder to understand, while presenting an authored 3D pose as physically exact
would overstate the evidence.

## Decision

- Operational Twin `1.5.0` exposes independently selected X/Y/Z position observations with unit, observation
  identity, and field-level provenance. Duplicate, missing, unavailable, or non-millimeter candidates do not
  silently select a value for that axis.
- The versioned `config/visualization/mazak01-observed-delta-mapping-v1.json` records a pinned source anchor and
  the minimum/maximum values observed in the pinned dataset for each axis.
- The renderer maps only the difference from that source anchor. It labels the resulting motion
  `OBSERVED_DELTA_MAPPING`; scene-axis assignment, baseline pose, and scale remain `SIMULATED`.
- Observed ranges are validation bounds, not machine travel limits. Values outside them are unavailable and are
  never clamped.
- The procedural model supplies explicit nested Z, X, and Y carriage references. Runtime binding uses those
  references without semantic-name lookup. A GLB without those optional references remains renderable but does
  not claim XYZ motion support.
- Stale or invalid input cannot advance an axis. Reduced-motion presentation applies a valid update immediately;
  ordinary continuously advancing replay may interpolate briefly as a visual transition.

## Consequences

Replay now shows source-derived XYZ change and the 2D view exposes the same observations and provenance. The
visual can look machine-like without being represented as an OEM kinematic model. Establishing physical zero,
sign, travel limits, collision envelopes, or exact Mazak geometry requires separate authoritative evidence and
a new mapping decision.
