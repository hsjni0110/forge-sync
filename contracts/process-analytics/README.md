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

`v1/anomaly-assessments.schema.json` describes immutable explainable assessments over one Cycle
Feature processing result. Create or reuse one with
`POST /api/v1/machines/{machineId}/anomaly-assessments/processing-runs`, supplying
`cycleFeatureProcessingRunId`, `baselinePolicyVersion: 1.0.0`, and
`anomalyAssessmentVersion: 1.0.0`. Read it with
`GET /api/v1/machines/{machineId}/anomaly-assessments?assessmentProcessingRunId=sha256:...`.
Responses use `application/vnd.forgesync.anomaly-assessments.v1+json`; first creation returns `201`
and deterministic reuse returns `200`.

The baseline group is machine, program, and Cycle Feature version. Only strictly earlier runs are
eligible. Each feature uses the latest 30 values with at least five samples; state and metric
features require coverage `>= 0.800000`, while duration only requires a value. Metric channel
identity includes metric, component, source DataItem, and unit. The response preserves candidates,
actual contributors, median/Q1/Q3/IQR, difference, direction, nullable percentage difference,
score, top reasons, source ranges, and `DERIVED -> DERIVED CycleFeature -> REAL:NIST` lineage. A
high deviation remains a Process Analytics result and does not create a Fault, Alarm, Advisory, or
command.

## Presentation at a stopped Replay Cursor

Machine Detail and Factory compose these contracts after a confirmed pause, completed seek or replay
end. They use the matching authoritative Twin cursor's session and sequence for segmentation, then
pass the returned processing identities into feature and assessment processing. All three responses
must agree before any bundle is shown. Explicit immutable references reuse the GET endpoints above.

CURRENT RUN describes the derived interval at that cursor. An open end is displayed as unconfirmed
with its original `INTERRUPTED`/`UNKNOWN` status. Feature/assessment detail is available by selecting
a completed run; a gap is not filled with a previous run. Recalculation creates/reuses immutable
results and invalidates the previous selection. During playback the UI displays analysis waiting.

Observation evidence carries a display-only `OBSERVED · REAL:NIST` label; wire provenance continues
to use its existing values. Processing versions, source locators, feature coverage and baseline
contributors remain accessible in evidence disclosures. See
[ADR-037](../../docs/adr/ADR-037-cursor-bound-process-analysis-presentation.md).
