# ADR-056: Layered Data Quality without an Overall Score

- Status: Accepted
- Date: 2026-09-13
- Related PRD: 67, 68, 79, 104, 105
- Extends: [ADR-020](./ADR-020-raw-source-preservation.md),
  [ADR-025](./ADR-025-postgres-ingestion-transaction.md),
  [ADR-050](./ADR-050-range-scoped-equipment-state-intervals.md)

## Context

ForgeSync has three different kinds of quality evidence: the immutable source/profile and semantic
mapping reports, replay-time delivery behavior, and derived process-analysis coverage. Combining
them into one percentage would erase their different denominators and could turn missing evidence
into an apparently good result. The PRD names six dimensions but does not define universal
good/warning/fault thresholds.

The current pinned Mazak01 mapping run has 101,693 mapped records out of 115,991 syntactically parsed
records (87.67318153994706%), 22 unknown records across 22 names, 14,276 intentionally unsupported
records across 13 DataItems, no invalid value/raw record, and no source ordering anomaly. The source
does not declare an expected delivery cadence or expected record count, so transport completeness
cannot be calculated honestly.

## Decision

- Publish the six dimensions separately and keep `overallGrade` explicitly null. Do not introduce
  inferred quality grades or default thresholds.
- Use `MEASURED` only when numerator and denominator are known. Use `NOT_EVALUATED` with a reason
  when they are not; never map that state to 0%, 100%, NORMAL, or success.
- Keep source profile, replay runtime, Twin freshness, run segmentation, and feature coverage as
  distinct layers in the versioned Data Quality `1.0.0` contract.
- Source validity and ordering use the pinned profile/mapping reports. Semantic Coverage is exactly
  mapped records divided by syntactically parsed records; unsupported items remain valid source
  records and are not silently discarded.
- Runtime duplication and ordering are durable PostgreSQL projections written in the canonical
  ingestion transaction. They are grouped by machine, replay session, and queried through an exact
  replay sequence. Duplicate delivery is distinct from duplicate business side effects.
- Per-session runtime validity is `NOT_EVALUATED`: a contract-rejected payload cannot provide a
  trusted canonical machine/session identity. Global transport rejection metrics remain observable,
  but are not reassigned to a session using untrusted payload fields.
- Completeness is `NOT_EVALUATED` until a source or transport contract declares an expected cadence
  or record count. The absence of observed gaps alone is not evidence of 100% completeness.
- Freshness reuses the Equipment Twin wall-clock policy and `projectedAt`; historical source time is
  not used for live freshness.
- Derived process quality reports input/result counts and AVAILABLE/PARTIAL/MISSING/EMPTY_WINDOW
  feature states. These do not alter source validity or Semantic Coverage.
- Unmapped DataItems keep classification, count, first `rawRecordId`, and a link to the pinned NIST
  source line. The dedicated `/data-quality` page and the Twin Data Quality section link to the same
  evidence without making Presentation the truth source.

## Consequences

- Operators see what was measured and why something could not be measured, but no unsupported
  pass/fail conclusion is made for 87.67% coverage.
- A future threshold requires a named, versioned product policy and evidence review rather than a
  CSS colour cutoff.
- Runtime rejected-message validity can only become replay-scoped if a trusted transport envelope
  supplies identity independently of the rejected canonical payload.
- The runtime quality projection adds bounded per-source-event rows so historical replay cursor
  queries do not leak later observations into earlier quality reports.
