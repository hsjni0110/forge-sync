import { ProcessAnalysisError } from "../application/ports";
import type { ProcessCursor } from "../domain/processAnalysis";
import type { AssessmentDocument, FeatureDocument, RunDocument } from "./processDocuments";

export function requireMatch(matches: boolean): void {
  if (!matches) throw new ProcessAnalysisError("VERSION_MISMATCH");
}

export function validateRunIdentity(runs: RunDocument, machineId: string, cursor: ProcessCursor): void {
  requireMatch(runs.machineId === machineId && runs.replaySessionId === cursor.replaySessionId &&
    runs.throughReplaySequence === cursor.replaySequence &&
    new Set(runs.machiningRuns.map((run) => run.machiningRunId)).size === runs.machiningRuns.length);
  for (const run of runs.machiningRuns) {
    const range = run.sourceObservationRange;
    requireMatch(run.machineId === machineId && run.processingRunId === runs.processingRunId &&
      range.replaySessionId === cursor.replaySessionId && range.firstReplaySequence <= range.lastReplaySequence &&
      range.lastReplaySequence <= cursor.replaySequence && run.startEvidence.replaySequence >= range.firstReplaySequence &&
      run.startEvidence.replaySequence <= range.lastReplaySequence &&
      (run.endedAt === undefined) === (run.endEvidence === undefined) &&
      (run.status !== "COMPLETED" || !!run.endEvidence) &&
      (!run.endEvidence || (run.endEvidence.replaySequence >= run.startEvidence.replaySequence &&
        run.endEvidence.replaySequence <= range.lastReplaySequence)));
  }
}

export function validateFeatureIdentity(features: FeatureDocument, runs: RunDocument): void {
  const completed = runs.machiningRuns.filter((run) => run.status === "COMPLETED");
  requireMatch(features.machineId === runs.machineId && features.machiningRunProcessingRunId === runs.processingRunId &&
    features.featureSets.length === completed.length &&
    new Set(features.featureSets.map((set) => set.machiningRunId)).size === completed.length &&
    new Set(features.featureSets.map((set) => set.cycleFeatureSetId)).size === completed.length);
  for (const set of features.featureSets) {
    requireMatch(completed.some((run) => run.machiningRunId === set.machiningRunId &&
      run.startedAt === set.aggregationWindow.startedAt && run.endedAt === set.aggregationWindow.endedAt) &&
      set.provenance.transformation.featureProcessingRunId === features.featureProcessingRunId &&
      set.sourceObservationRange.replaySessionId === runs.replaySessionId &&
      set.sourceObservationRange.lastReplaySequence <= runs.throughReplaySequence);
  }
}

export function validateAssessmentIdentity(assessments: AssessmentDocument, features: FeatureDocument): void {
  requireMatch(assessments.machineId === features.machineId &&
    assessments.machiningRunProcessingRunId === features.machiningRunProcessingRunId &&
    assessments.cycleFeatureProcessingRunId === features.featureProcessingRunId &&
    assessments.assessments.length === features.featureSets.length &&
    new Set(assessments.assessments.map((item) => item.targetFeatureSetId)).size === features.featureSets.length);
  for (const item of assessments.assessments) {
    requireMatch(features.featureSets.some((set) => set.cycleFeatureSetId === item.targetFeatureSetId &&
      set.machiningRunId === item.machiningRunId) && item.baseline.machineId === features.machineId &&
      item.baseline.targetFeatureSetId === item.targetFeatureSetId &&
      item.lineage.inputCycleFeature.featureProcessingRunId === features.featureProcessingRunId);
  }
}
