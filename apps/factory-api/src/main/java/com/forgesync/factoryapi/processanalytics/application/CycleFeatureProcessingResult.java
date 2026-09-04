package com.forgesync.factoryapi.processanalytics.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CycleFeatureProcessingResult(
    String featureProcessingRunId,
    String machiningRunProcessingRunId,
    String machineId,
    String cycleFeatureVersion,
    String inputHash,
    int inputObservationCount,
    int eligibleRunCount,
    String resultHash,
    Instant createdAt,
    boolean isCreated,
    List<CycleFeatureSet> featureSets) {
  public CycleFeatureProcessingResult {
    Objects.requireNonNull(featureProcessingRunId);
    Objects.requireNonNull(machiningRunProcessingRunId);
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(cycleFeatureVersion);
    Objects.requireNonNull(inputHash);
    Objects.requireNonNull(resultHash);
    Objects.requireNonNull(createdAt);
    featureSets = List.copyOf(featureSets);
  }

  public CycleFeatureProcessingResult asExisting() {
    return new CycleFeatureProcessingResult(
        featureProcessingRunId,
        machiningRunProcessingRunId,
        machineId,
        cycleFeatureVersion,
        inputHash,
        inputObservationCount,
        eligibleRunCount,
        resultHash,
        createdAt,
        false,
        featureSets);
  }
}
