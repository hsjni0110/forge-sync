package com.forgesync.factoryapi.processanalytics.application;

public final class CycleFeatureProcessingNotFoundException extends RuntimeException {
  public CycleFeatureProcessingNotFoundException(String processingRunId) {
    super("Cycle Feature processing result not found: " + processingRunId);
  }
}
