# ADR-045 — Observed Tool Change Timeline

## Status

Accepted — 2026-09-06

## Context

The Operational Twin exposes the latest observed tool number, but a browser cannot reconstruct
earlier changes after a seek from that single value. Browser-only history would make markers depend
on navigation history. The NIST profile identifies `Tool_number` but no verified tool geometry.

## Decision

- A versioned read-only Tool Change Timeline queries Canonical `TOOL_NUMBER` history for one
  machine and Replay session through an explicit Replay sequence.
- The first available value is a baseline. Equal values create no transition. `UNAVAILABLE` breaks
  continuity, so no change is inferred across an observation gap.
- Every transition preserves source time, Replay sequence, DataItem ID, source set, artifact,
  raw-record locator, and mapping version. Observed zero remains zero and is not called unmounted.
- Runtime binding updates the existing `toolMount` reference and never creates a node per update.
- The model uses a shape-neutral placeholder and states `OBSERVED · 형상 미확인`; it does not claim
  a drill, mill, holder, or PHM cutter identity.
- Timeline lookup failure removes optional markers only. Twin, spindle, B-axis, and 2D detail remain
  available.

## Consequences

Markers are reproducible after seek and bounded by the authoritative Replay cursor. A future
verified tool registry can replace the neutral representation without changing transition meaning.
