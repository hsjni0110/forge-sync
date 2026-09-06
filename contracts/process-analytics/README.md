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
`anomalyAssessmentVersion: 2.0.0`. Read it with
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

### Primary feature and supporting channels (`anomalyAssessmentVersion: 3.0.0`)

`classification` and `score` come from one feature, named in `primaryFeatureKey`:
`durationSeconds`, the cycle outcome an operator acts on. Every other feature is still compared
and still reported in `contributions`; the count of those that cross the deviating threshold is
`supportingOutlierCount`, and `topReasons` leads with the primary feature followed by the strongest
others. Without a primary contribution there is nothing to grade, so `dataStatus` is
`INSUFFICIENT_DATA` and `classification` is null whatever the channels show.

Version `2.0.0` classified on the maximum over every feature. A run is compared against a median of
thirty features at once, so the most extreme of them was almost always past the threshold: the top
reason was a metric channel in 92 of 94 evaluable runs, a median of 5.5 features crossed the
threshold together, only 4 runs had exactly one crossing, and in 73 runs the cycle duration was
within baseline while a channel carried the label. Selecting the maximum of thirty simultaneous
comparisons reports the most extreme channel rather than the state of the process.

| | `NORMAL` | `DEVIATING` | `HIGH_DEVIATION` |
|---|---:|---:|---:|
| `2.0.0` | 3 | 46 | 45 |
| `3.0.0` | 76 | 7 | 11 |

Over the same 94 evaluable runs, 76 (81%) are within baseline, and a median of 5 channels are
reported beside the grade rather than as it. A high deviation, and any number of channel outliers,
remains a Process Analytics result and does not create a Fault, Alarm, Advisory, or command.

### Deviation scale and floors (`anomalyAssessmentVersion: 2.0.0` and later)

Each contribution reports the `deviationScale` it was measured against and a `reasonCode` naming
which input bound that scale:

| `reasonCode` | Bound by | Value |
|---|---|---|
| `BASELINE_IQR_DISTANCE` | observed sample spread | interquartile range |
| `RELATIVE_SCALE_FLOOR` | ordinary-variation floor | `0.10 * abs(median)` |
| `ABSOLUTE_SCALE_FLOOR` | resolution floor | `1` second, duration features only |
| `ZERO_SCALE_DEVIATION` | no scale could be established | median, spread and floors are all zero |

`distance` is the absolute difference divided by that scale, and `score` maps it through
`d / (1 + d)`. A distance below `1` is `NORMAL`, below `3` is `DEVIATING`, and `3` or more is
`HIGH_DEVIATION`.

Version `1.0.0` divided by the interquartile range alone. On the NIST Mazak01 observation set the
cycle is consistent to about a second, so the interquartile range was one to two seconds and a
twelve second difference measured six to twelve ranges: 92 of the 94 evaluable runs (98%) were
reported as deviating and only 2 as within baseline, while the actual spread was 8.8% to 10.8%.
The floors set what counts as ordinary variation before the sample spread is allowed to speak.
Cutting cycles move by a few percent from tool wear, material variation and operator overrides
without the process having changed, and cycle boundaries come from discrete execution events so
sub-second differences carry no meaning. Assessments produced under `1.0.0` are not rewritten.

Measured over the same full NIST observation set (122 runs, 94 evaluable, 2,820 contributions):

| | `NORMAL` | `DEVIATING` | `HIGH_DEVIATION` |
|---|---:|---:|---:|
| `1.0.0` | 2 | 28 | 64 |
| `2.0.0` | 3 | 46 | 45 |
| `2.0.0`, `durationSeconds` alone | 76 | 7 | 11 |

The floors bound the scale for 915 relative and 89 absolute of the 2,820 contributions. Under
`2.0.0` the reported classification barely moved because it was the maximum over every feature;
`3.0.0` grades on the cycle duration these floors were written for, and the two changes together
take the same runs from 2 within baseline to 76.

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
