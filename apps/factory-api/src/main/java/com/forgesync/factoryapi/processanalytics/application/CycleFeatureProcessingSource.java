package com.forgesync.factoryapi.processanalytics.application;

import java.util.Optional;

public interface CycleFeatureProcessingSource {
  Optional<CycleFeatureProcessingResult> findCycleFeatureProcessingRun(
      String featureProcessingRunId);
}
