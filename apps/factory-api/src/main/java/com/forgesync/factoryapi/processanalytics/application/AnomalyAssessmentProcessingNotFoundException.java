package com.forgesync.factoryapi.processanalytics.application;

public final class AnomalyAssessmentProcessingNotFoundException extends RuntimeException {
  public AnomalyAssessmentProcessingNotFoundException(String id) {
    super("Anomaly Assessment processing run not found: " + id);
  }
}
