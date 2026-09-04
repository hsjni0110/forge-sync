package com.forgesync.factoryapi.processanalytics.application;

import java.util.Optional;

public interface CycleFeatureProjectionStore {
  boolean preserve(CycleFeatureProcessingResult result);

  Optional<CycleFeatureProcessingResult> findCycleFeatureProcessingRun(
      String featureProcessingRunId);
}
