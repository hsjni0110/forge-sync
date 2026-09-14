export type MeasurementStatus = "MEASURED" | "NOT_EVALUATED";

export interface QualityMeasurement {
  status: MeasurementStatus;
  numerator: number;
  denominator: number;
  ratio: number | null;
  grade: null;
  basis: string;
  reason: string | null;
}

export interface LayeredQualityDimension {
  sourceProfile: QualityMeasurement;
  runtime: QualityMeasurement;
}

export interface DataQualityReport {
  schemaVersion: "1.0.0";
  machineId: string;
  sourceSetId: string;
  replaySessionId: string | null;
  throughReplaySequence: number | null;
  evaluatedAt: string;
  overallGrade: null;
  dimensions: {
    validity: LayeredQualityDimension;
    completeness: QualityMeasurement;
    ordering: LayeredQualityDimension;
    duplication: LayeredQualityDimension;
    freshness: {
      status: MeasurementStatus;
      value: "FRESH" | "LAGGING" | "STALE" | null;
      ageMillis: number | null;
      freshMaxAgeMillis: number | null;
      laggingMaxAgeMillis: number | null;
      basis: "PROJECTED_AT_WALL_CLOCK";
      reason: string | null;
    };
    semanticCoverage: QualityMeasurement & {
      status: "MEASURED";
      ratio: number;
      mappingVersion: string;
      processingRunId: string;
    };
  };
  runtime: {
    status: MeasurementStatus;
    receivedCount: number;
    acceptedCount: number;
    duplicateCount: number;
    outOfOrderCount: number;
    firstIngestedAt: string | null;
    lastIngestedAt: string | null;
    scope: "REPLAY_SESSION_THROUGH_SEQUENCE";
  };
  derivedProcess: {
    segmentation: {
      status: MeasurementStatus;
      processingRunId: string | null;
      inputObservationCount: number;
      resultCount: number;
      reason: string | null;
    };
    featureCoverage: {
      status: MeasurementStatus;
      processingRunId: string | null;
      eligibleRunCount: number;
      availableCount: number;
      partialCount: number;
      missingCount: number;
      emptyWindowCount: number;
      reason: string | null;
    };
  };
  unmappedDataItems: Array<{
    classification: "UNKNOWN" | "AMBIGUOUS" | "UNSUPPORTED";
    dataItemId: string | null;
    name: string;
    recordCount: number;
    firstRawRecordId: string;
    evidenceHref: string;
  }>;
  evidence: {
    sourceProfileProcessingRunId: string;
    semanticMappingProcessingRunId: string;
    rawArtifactId: string;
    mappingVersion: string;
    sourceHref: string;
  };
}

export interface DataQualityScope {
  replaySessionId: string;
  throughReplaySequence: number;
}
