# Data Quality API contract

`v1/data-quality-report.schema.json` defines the machine quality report returned with media type
`application/vnd.forgesync.data-quality.v1+json`.

The report does not publish an overall score or inferred good/warning/fault grade. Source profile,
replay-session runtime, Twin freshness, semantic mapping, run segmentation, and feature coverage
remain separate evidence layers. `NOT_EVALUATED` means the required denominator or trusted
identity does not exist; clients must not render it as 0%, 100%, NORMAL, or success.

When `replaySessionId` and `throughReplaySequence` are present, both are required and runtime counts
refer only to observations at or before that cursor. Every unmapped DataItem retains the first raw
record identity and a pinned source link.
