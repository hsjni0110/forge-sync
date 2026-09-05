package com.forgesync.factoryapi.processanalytics.application;

public interface FindAnomalyAssessments {
  AnomalyAssessmentProcessingResult find(String machineId, String assessmentProcessingRunId);
}
