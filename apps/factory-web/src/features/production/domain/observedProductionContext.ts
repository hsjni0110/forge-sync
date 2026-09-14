export interface ProductionEvidence {
  replaySequence: number;
  sourceObservedAt: string;
  sourceEventKey: string;
  rawRecordId: string;
  sourceDataItemId: string;
}

export interface ObservedProductionContext {
  schemaVersion: "1.0.0";
  ruleVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  machiningRunProcessingRunId: string;
  programIntervals: Array<{
    kind: "MAIN" | "SUBPROGRAM";
    availability: "AVAILABLE" | "UNAVAILABLE";
    programName?: string;
    startedAt: string;
    endedAt?: string;
    startEvidence: ProductionEvidence;
    endEvidence?: ProductionEvidence;
  }>;
  programSummaries: Array<{
    programName: string;
    runCount: number;
    completedRunCount: number;
    totalDurationSeconds: number;
    meanDurationSeconds?: number;
    medianDurationSeconds?: number;
    machiningRunIds: string[];
  }>;
  unassignedRunCount: number;
  partCount: {
    status: "AVAILABLE" | "UNAVAILABLE";
    netIncrease?: number;
    usedTransitionCount: number;
    resetCount: number;
    unavailableObservationCount: number;
    reason?: string;
    associations: Array<{
      observedIncrease: number;
      relationship: "TEMPORAL_OVERLAP_ONLY";
      overlappingMachiningRunIds: string[];
    }>;
  };
  productionResultStatus: "NOT_OBSERVED";
}
