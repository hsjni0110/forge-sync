package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import java.util.Objects;

public record ProcessCycleFeaturesCommand(
    String machineId, String machiningRunProcessingRunId, String cycleFeatureVersion) {
  public ProcessCycleFeaturesCommand {
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(machiningRunProcessingRunId);
    Objects.requireNonNull(cycleFeatureVersion);
    if (!cycleFeatureVersion.equals(CycleFeatureExtractor.FEATURE_VERSION)) {
      throw new IllegalArgumentException("Unsupported cycleFeatureVersion");
    }
  }
}
