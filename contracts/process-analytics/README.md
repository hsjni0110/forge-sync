# Process Analytics Contracts

`v1/machining-runs.schema.json` describes one immutable deterministic segmentation result. The
processing identity binds the machine, Replay Session watermark, ordered Canonical Observation
content, and segmentation rule version. Reprocessing never changes an earlier document.

Machining Runs are `DERIVED` facts whose nested source remains `REAL:NIST`. They are not Production
`OperationExecution` or `ProductionResult` records. Missing evidence stays absent and lowers the
reported confidence; it is never invented.

Create or reuse a deterministic batch result with:

```http
POST /api/v1/machines/Mazak01/machining-runs/processing-runs
Content-Type: application/json

{
  "replaySessionId": "00d64db8-967e-41ba-9d09-fdd087710aac",
  "throughReplaySequence": 1509,
  "segmentationRuleVersion": "1.0.0"
}
```

The first request returns `201 Created`; an identical input returns the same result with `200 OK`.
Read an immutable version with
`GET /api/v1/machines/{machineId}/machining-runs?processingRunId=sha256:...`. Both responses use
`application/vnd.forgesync.machining-runs.v1+json`.

`v1/cycle-features.schema.json` describes immutable feature processing results derived only from
`COMPLETED` Machining Runs. Create or reuse one with
`POST /api/v1/machines/{machineId}/cycle-features/processing-runs`, supplying
`machiningRunProcessingRunId` and `cycleFeatureVersion: 1.0.0`; read it with
`GET /api/v1/machines/{machineId}/cycle-features?featureProcessingRunId=sha256:...`. Responses use
`application/vnd.forgesync.cycle-features.v1+json`.

Windows are `[startedAt, endedAt)`. State duration uses carried-forward `EXECUTION` observations;
only `ACTIVE` is cutting and known non-active values are idle. Metric channels remain separate by
metric, component, source data item, and unit. Mean and population standard deviation are
time-weighted; missing intervals remain uncovered rather than becoming zero. Every feature reports
coverage, calculation, observation range, `DERIVED` transformation provenance, and nested
`REAL:NIST` source provenance.
