package com.forgesync.factoryapi.processanalytics.domain;

import java.time.Instant;
import java.util.List;

public record CycleBaseline(
    String baselineGroupId,
    String machineId,
    String programName,
    String cycleFeatureVersion,
    String baselinePolicyVersion,
    String targetFeatureSetId,
    List<String> candidateFeatureSetIds,
    Instant trainingStartedAt,
    Instant trainingEndedAt,
    List<ObservationRange> trainingSourceRanges,
    List<FeatureBaseline> featureBaselines) {
  public CycleBaseline {
    candidateFeatureSetIds = List.copyOf(candidateFeatureSetIds);
    trainingSourceRanges = List.copyOf(trainingSourceRanges);
    featureBaselines = List.copyOf(featureBaselines);
  }
}
