# ADR-037: Cursor-bound Process Analysis Presentation

- Status: Accepted
- Date: 2026-09-05
- Extends: [ADR-032](./ADR-032-observed-process-analytics-boundary.md),
  [ADR-033](./ADR-033-replay-control-cursor-and-seek.md),
  [ADR-034](./ADR-034-deterministic-machining-run-segmentation.md),
  [ADR-036](./ADR-036-explainable-cycle-baseline-and-anomaly-assessment.md)

## Context

Machine Detail and Factory need to explain the run at the authoritative Replay Cursor. Existing
segmentation is immutable batch analysis: an open boundary remains `INTERRUPTED` or `UNKNOWN`.
Cycle Feature v1 accepts only completed runs. Presenting these as live `RUNNING` analysis would
silently change accepted meaning. The user selected analysis at paused/seek-completed/source-ended
positions, with full detail and a compact Factory summary.

## Decision

- Share one browser Replay controller between controls and analysis. Observe running sessions every
  1,000 ms and preparing/seeking sessions every 250 ms. Optimistic command state is not confirmed
  session state and cannot authorize analysis. Polls and commands have generation guards.
- Analyze only after a confirmed `PAUSED` or `COMPLETED` publication cursor agrees with the connected
  Twin's session, sequence and source/publication instants. Preserve sub-millisecond precision when
  comparing instants. Historical STALE data remains inspectable with its existing stale indication.
- Compose existing immutable run, feature and assessment v1 REST results through their processing
  identities, in order. No new wire format, service, worker, database table or analysis rule is added.
  Explicit processing references use the existing GET queries. Explicit recalculation uses the
  existing idempotent POST operations and preserves earlier results.
- Validate schemas, machine/session/watermark, one-to-one completed-run/feature/assessment identity,
  feature windows and provenance links before publishing the whole analysis bundle. Bind the bundle
  to the requesting Twin cursor/version; never interpret processing IDs as Twin versions.
- Invalidated requests cannot publish results. Seek, resume, a changed Twin cursor/version and
  recalculation hide the old bundle. Version disagreement invokes Replay/Twin REST resync with at
  most five retries at 250/500/1,000/2,000/4,000 ms; failures remain visible with manual retry.
- CURRENT RUN is the cursor-containing derived interval, using start-inclusive/end-exclusive
  sequence boundaries. An open interval retains its terminal batch status and is labeled as missing
  end evidence. A gap remains empty. Selecting a historical run does not replace CURRENT RUN.
- List and marker selection open details without seeking. Explicit marker navigation sends its
  original timestamp through the existing seek command and rebuilds against the new session.
- `OBSERVED` labels observation evidence in the UI, not a new provenance enum. Runs, features and
  assessments remain `DERIVED` with nested `REAL:NIST` lineage. Missing features/coverage and
  insufficient baseline samples are not zero values or normality claims. Deviations do not create
  Fault, Alarm, Advisory or command state.
- Factory's compact panel offers current-run context and a detail link; its 2D view and Machine
  Detail offer the complete list, features, comparison reasons and evidence. WebGL failure does not
  remove these capabilities.

## Cursor regression exposed by integration

Investigation of a seek cursor mismatch exposed a pre-existing cross-DataItem ordering issue: each
latest DataItem was guarded, but a late update of another DataItem could move the machine cursor
backwards. A PostgreSQL regression reproduces this with sequence 42 followed by sequence 41 for
different DataItems. This regression is separate from the observed E2E delivery gaps; fixing it
alone did not resolve the E2E failure.

Keep the highest projected sequence's source/publication cursor inside the same Replay Session when
versioning a newly projected DataItem. The accepted DataItem still advances TwinVersion and updates
Equipment State atomically as in ADR-026; its field provenance retains its own observation times.
A newly activated session may move source time backwards as specified by ADR-033. This is a repair
of the existing cursor-order invariant, not a history deletion, migration or new delivery guarantee.

## Scope

This decision governs analysis of the run AT a cursor. Range-scoped aggregation over everything a
Replay Session has observed — equipment state dwell time and the utilization figures built on it —
answers a different question and is governed by
[ADR-050](./ADR-050-range-scoped-equipment-state-intervals.md). The invalidation rules here do not
hide those results, and they do not depend on a confirmed cursor.

## Consequences and limits

- Each processing stage retains its own transaction. The browser publishes a coherent bundle, not
  a claim of a distributed transaction or a complete contiguous ingestion watermark.
- Aborting a browser request prevents stale presentation; it does not promise cancellation of an
  already-running server transaction. No analysis is computed continuously during playback.
- JSON Schema decimal multiples are checked using the parsed number's decimal representation,
  avoiding binary-division rejection of valid values without relaxing declared precision.
- Browser POST preflights are allowed only for the three analysis processing endpoints and the
  configured origin allowlist. Other machine resources remain GET-only across origins.
- No live feature/anomaly policy, production lifecycle, physical 3D model or new provenance meaning
  is introduced.

## Verification

- Frontend behavior and shared-contract tests cover open/empty runs, completed selection, coverage,
  insufficient samples, explicit GET/recalculation, invalid versions/joins, decimal precision,
  response reversal, bounded resync, natural replay completion and unconfirmed commands.
- PostgreSQL integration covers the cross-DataItem cursor regression while preserving both history
  rows, DataItem values and atomic Twin/Equipment State versions.
- Browser acceptance seeks across the reviewed NIST run, compares 2D/3D/analysis versions and uses
  keyboard-accessible selection/evidence. Actual command outcomes are recorded in the Ledger.
