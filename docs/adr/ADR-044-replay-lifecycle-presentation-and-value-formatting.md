# ADR-044 — Replay Lifecycle Presentation and Value Formatting

## Status

Accepted — 2026-09-06

## Context

Twin freshness correctly becomes `STALE` when no new projection arrives, including after a replay
has normally completed or has intentionally paused. Presenting that classification alone made a
completed replay look like a live-data fault. Raw decimal precision, long timestamps, and a flat
provenance list also obscured the operational meaning without adding evidence.

## Decision

- Freshness calculation and the Operational Twin contract remain unchanged. Presentation combines
  freshness with the authoritative Replay lifecycle only at the UI boundary.
- `COMPLETED` is presented as the last replay data and `PAUSED` as selected-time data. Neither uses
  the realtime misuse warning. A `STALE` Twin without either lifecycle state retains the danger
  warning and existing animation freeze.
- UTC remains the display timezone. Visible timestamps separate date, second-precision time, and an
  explicit UTC badge; the exact source string remains in semantic `datetime` and title attributes.
- Shared numeric formatting rounds only the display value and trims insignificant zeroes. Stored,
  transported, and provenance values are not rewritten.
- Missing observations remain `확인할 수 없음`. An observed tool number of zero remains zero and
  is labelled `미장착 여부 확인 불가`; it is not converted into an unsupported machine-state claim.
- Provenance is grouped by source identity and source-set identity, counted, and collapsed by
  default. Every field-level locator remains available after expansion.

## Consequences

Dashboard, Machine Detail, Replay controls, and process analysis use consistent readable formatting
without weakening data lineage. Replay lifecycle is presentation context rather than a new Twin
truth field. Consumers that need full timestamp precision continue to use the contract value.
