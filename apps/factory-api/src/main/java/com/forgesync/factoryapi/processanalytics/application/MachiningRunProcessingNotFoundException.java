package com.forgesync.factoryapi.processanalytics.application;

public final class MachiningRunProcessingNotFoundException extends RuntimeException {
  public MachiningRunProcessingNotFoundException(String processingRunId) {
    super("Machining Run processing result not found: " + processingRunId);
  }
}
