package com.forgesync.factoryapi.processanalytics.application;

public interface ProcessAnomalyAssessments {
  AnomalyAssessmentProcessingResult process(ProcessAnomalyAssessmentsCommand command);
}
