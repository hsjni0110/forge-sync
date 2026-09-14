export interface ToolLoadTrendEvidence {
  replaySequence: number;
  sourceObservedAt: string;
  sourceEventKey: string;
  rawRecordId: string;
  sourceDataItemId: string;
  mappingVersion: string;
}

export interface ToolLoadTrendReport {
  schemaVersion: "1.0.0";
  policyVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  machiningRunProcessingRunId: string;
  policy: {
    minimumRawSamplesPerPoint: 3;
    minimumTrendPoints: 5;
    baselinePointCount: 3;
    minimumCoverageRatio: 0.8;
    pointFormula: string;
    coverageFormula: string;
    deviationFormula: string;
    slopeFormula: string;
  };
  provenance: {
    origin: "DERIVED";
    sourceKind: "REAL" | "UNKNOWN";
    provider: string;
    sourceSetId: string;
  };
  groups: ToolLoadTrendGroup[];
}

export interface ToolLoadTrendGroup {
  programName: string;
  toolNumber: number;
  componentId: string;
  sourceDataItemId: string;
  unit: "PERCENT";
  status: "AVAILABLE" | "INSUFFICIENT_SAMPLES" | "INSUFFICIENT_COVERAGE" | "BASELINE_UNAVAILABLE";
  reasons: string[];
  candidatePointCount: number;
  eligiblePointCount: number;
  coverageRatio: number;
  baselineMedianLoad?: number;
  latestDeviationPercent?: number;
  slopePercentPerPoint?: number;
  points: Array<{
    machiningRunId: string;
    status: "AVAILABLE" | "INSUFFICIENT_SAMPLES";
    availableSampleCount: number;
    totalObservationCount: number;
    medianLoad?: number;
    deviationPercent?: number;
    firstEvidence: ToolLoadTrendEvidence;
    lastEvidence: ToolLoadTrendEvidence;
  }>;
}
