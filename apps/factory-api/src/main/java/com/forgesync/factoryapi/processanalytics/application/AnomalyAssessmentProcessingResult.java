package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessment;
import java.time.Instant;
import java.util.List;

public record AnomalyAssessmentProcessingResult(
    String assessmentProcessingRunId,
    String cycleFeatureProcessingRunId,
    String machiningRunProcessingRunId,
    String machineId,
    String cycleFeatureVersion,
    String baselinePolicyVersion,
    String anomalyAssessmentVersion,
    String inputHash,
    String resultHash,
    Instant createdAt,
    boolean isCreated,
    List<AnomalyAssessment> assessments) {
  public AnomalyAssessmentProcessingResult {
    assessments = List.copyOf(assessments);
  }

  public AnomalyAssessmentProcessingResult asExisting() {
    return new AnomalyAssessmentProcessingResult(
        assessmentProcessingRunId,
        cycleFeatureProcessingRunId,
        machiningRunProcessingRunId,
        machineId,
        cycleFeatureVersion,
        baselinePolicyVersion,
        anomalyAssessmentVersion,
        inputHash,
        resultHash,
        createdAt,
        false,
        assessments);
  }
}
