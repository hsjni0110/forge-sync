import type { MachiningRun } from "../domain/processAnalysis";

export interface RunDocument {
  machineId: string; replaySessionId: string; throughReplaySequence: number; processingRunId: string;
  machiningRuns: Array<{
    machiningRunId: string; processingRunId: string; machineId: string;
    status: MachiningRun["status"]; programName?: string; startedAt: string; endedAt?: string;
    startEvidence: { replaySequence: number }; endEvidence?: { replaySequence: number };
    sourceObservationRange: ObservationRange;
    supportingEvidence: unknown[]; provenance: unknown;
    confidence: { level: string; reasons: string[] };
  }>;
}
export interface ObservationRange {
  replaySessionId: string; firstReplaySequence: number; lastReplaySequence: number;
}
export interface FeatureDocument {
  machineId: string; machiningRunProcessingRunId: string; featureProcessingRunId: string;
  featureSets: Array<{
    cycleFeatureSetId: string; machiningRunId: string; status: string;
    aggregationWindow: { startedAt: string; endedAt: string; durationSeconds: number };
    stateFeatures: { cuttingSeconds: number | null; idleSeconds: number | null; coverage: Coverage };
    metricFeatures: Array<{
      metric: string; componentId?: string; sourceDataItemId?: string; unit?: string; status: string;
      mean?: number; maximum?: number; populationStandardDeviation?: number; coverage: Coverage;
    }>;
    sourceObservationRange: ObservationRange;
    provenance: { transformation: { featureProcessingRunId: string } };
  }>;
}
interface Coverage { ratio: number | null }
export interface AssessmentDocument {
  machineId: string; machiningRunProcessingRunId: string;
  cycleFeatureProcessingRunId: string; assessmentProcessingRunId: string;
  assessments: Array<{
    machiningRunId: string; targetFeatureSetId: string; dataStatus: string;
    classification?: string | null; score?: number | null;
    baseline: {
      machineId: string; targetFeatureSetId: string;
      featureBaselines: Array<{ featureKey: string; sampleCount: number; unavailableReason?: string | null }>;
    };
    lineage: { inputCycleFeature: { featureProcessingRunId: string } };
    topReasons: Array<{
      featureKey: string; targetValue: number; baselineMedian: number; difference: number;
      percentageDifference?: number | null; direction: string; score: number;
      sampleCount: number; contributingFeatureSetIds: string[]; reasonCode: string;
    }>;
  }>;
}
