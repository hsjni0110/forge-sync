export type ComponentStatus = "AVAILABLE" | "UNAVAILABLE";
export type ValueProvenance = "OBSERVED" | "DERIVED" | "ASSUMED" | "UNAVAILABLE";

export interface OperationalEffectivenessReport {
  schemaVersion: "1.0.0";
  processingRunId: string;
  createdAt: string;
  policyVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  observedFrom: string;
  observedTo: string;
  utilizationProcessingRunId: string;
  cycleFeatureProcessingRunId: string;
  machiningRunProcessingRunId: string;
  targetFeatureSetId?: string;
  programName?: string;
  inputHash: string;
  resultHash: string;
  availability: {
    status: ComponentStatus;
    percent?: number;
    sourceProvenance: ValueProvenance;
    valueProvenance: ValueProvenance;
    formula: string;
    reason?: string;
  };
  performance: {
    status: ComponentStatus;
    percent?: number;
    actualCycleSeconds?: number;
    referenceSeconds?: number;
    referenceKind?: string;
    provenance: ValueProvenance;
    sampleCount: number;
    contributingFeatureSetIds: string[];
    reason?: string;
  };
  throughput: {
    status: ComponentStatus;
    partCount?: number;
    usedTransitionCount: number;
    resetCount: number;
    unavailableObservationCount: number;
    reason?: string;
  };
  quality: { status: "UNAVAILABLE"; provenance: "UNAVAILABLE"; reason: string };
  compositeOee: {
    status: "UNAVAILABLE";
    provenance: "UNAVAILABLE";
    reason: string;
  };
}
