# ADR-042 — Dense Machining Run Presentation

## Status

Accepted — 2026-09-06

## Context

The full Mazak01 observation interval produces 122 machining runs. Rendering every run with a
minimum two-percent width made 116 bars overlap, while the list required about 21.6 viewports of
scrolling. The former overview also started at the first run rather than the Replay source range,
which hid the leading idle interval.

## Decision

- The overview always uses the authoritative Replay Session `sourceRange`; filtering changes the
  visible runs, not the axis.
- Up to 32 runs are rendered as their exact intervals without an artificial minimum width. Denser
  results are assigned to 32 non-overlapping, equal-time bins. Each non-empty bin reports its run
  count and the UI states that individual intervals were omitted from the overview.
- Filters cover program, assessment classification, and inclusive minimum/maximum completed
  duration. Summaries are grouped by program and calculated only from the filtered runs. Each group
  reports count, median completed duration, total completed duration, and classification counts.
- If a filter excludes an explicitly selected run, selection is cleared and announced. Applying a
  filter never seeks or changes the authoritative Replay Cursor.
- The list initially renders 40 runs and adds 40 on explicit request. This bounds the initial DOM for
  the observed 122-run data without adding a virtualization dependency. Step 47 will define and
  measure the final browser frame-time budget.

## Consequences

The overview preserves idle time and density but aggregated bins are not individual run selectors;
the accessible list remains the individual-run interaction surface. No Process Analytics, Replay,
or Twin wire contract changes. Performance is a measured project baseline, not an industrial SLA.
