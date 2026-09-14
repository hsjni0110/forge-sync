package com.forgesync.factoryapi.production.application;

public final class ProductionContextInputNotFoundException extends RuntimeException {
  public ProductionContextInputNotFoundException(String processingRunId, Throwable cause) {
    super("Machining Run processing input was not found: " + processingRunId, cause);
  }
}
