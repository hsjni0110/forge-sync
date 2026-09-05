package com.forgesync.factoryapi.processanalytics.application;

import java.util.Optional;

public interface AnomalyAssessmentProjectionStore {
  boolean preserve(AnomalyAssessmentProcessingResult result);

  Optional<AnomalyAssessmentProcessingResult> findAnomalyAssessmentProcessingRun(
      String assessmentProcessingRunId);
}
