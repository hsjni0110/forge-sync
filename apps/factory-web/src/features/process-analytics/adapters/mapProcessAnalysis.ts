import type { RunAnalysis, TraceEntry } from "../domain/processAnalysis";
import type { AssessmentDocument, FeatureDocument, RunDocument } from "./processDocuments";

export function traceEntries(value: unknown, path = ""): TraceEntry[] {
  if (value !== null && typeof value === "object") {
    return Object.entries(value).flatMap(([key, child]) => traceEntries(child, path ? `${path} / ${key}` : key));
  }
  return [{ label: path, value: value === null ? "확인할 수 없음" : String(value) }];
}

export function mapProcessAnalysis(runs: RunDocument, features: FeatureDocument, assessments: AssessmentDocument): RunAnalysis {
  return {
    processingId: runs.processingRunId, featureProcessingId: features.featureProcessingRunId,
    assessmentProcessingId: assessments.assessmentProcessingRunId,
    runs: runs.machiningRuns.map((run) => {
      const feature = features.featureSets.find((set) => set.machiningRunId === run.machiningRunId);
      const assessment = assessments.assessments.find((item) => item.machiningRunId === run.machiningRunId);
      return {
        id: run.machiningRunId, status: run.status, program: run.programName,
        startedAt: run.startedAt, endedAt: run.endedAt, startSequence: run.startEvidence.replaySequence,
        endSequence: run.endEvidence?.replaySequence, confidence: run.confidence.level,
        reasons: run.confidence.reasons, evidence: traceEntries({ startEvidence: run.startEvidence,
          endEvidence: run.endEvidence ?? null, supportingEvidence: run.supportingEvidence,
          sourceObservationRange: run.sourceObservationRange, provenance: run.provenance }),
        feature: feature && {
          id: feature.cycleFeatureSetId, status: feature.status,
          durationSeconds: feature.aggregationWindow.durationSeconds,
          cuttingSeconds: feature.stateFeatures.cuttingSeconds, idleSeconds: feature.stateFeatures.idleSeconds,
          coverageRatio: feature.stateFeatures.coverage.ratio, evidence: traceEntries(feature),
          metrics: feature.metricFeatures.map((metric) => ({
            metric: metric.metric, component: metric.componentId, sourceDataItem: metric.sourceDataItemId,
            unit: metric.unit, status: metric.status, mean: metric.mean, maximum: metric.maximum,
            standardDeviation: metric.populationStandardDeviation,
            coverageRatio: metric.coverage.ratio, evidence: traceEntries(metric),
          })),
        },
        assessment: assessment && {
          status: assessment.dataStatus, classification: assessment.classification,
          score: assessment.score, evidence: traceEntries(assessment),
          primaryFeature: assessment.primaryFeatureKey ?? null,
          supportingOutlierCount: assessment.supportingOutlierCount ?? 0,
          reasons: assessment.topReasons.map((reason) => ({ feature: reason.featureKey,
            target: reason.targetValue, median: reason.baselineMedian, difference: reason.difference,
            percentage: reason.percentageDifference, direction: reason.direction,
            sampleCount: reason.sampleCount, code: reason.reasonCode,
            distance: reason.distance ?? null, scale: reason.deviationScale ?? null })),
          featureBaselines: assessment.baseline.featureBaselines.map((baseline) => ({
            feature: baseline.featureKey, sampleCount: baseline.sampleCount,
            unavailableReason: baseline.unavailableReason ?? null,
          })),
        },
      };
    }).sort((a, b) => a.startSequence - b.startSequence || a.id.localeCompare(b.id)),
  };
}
