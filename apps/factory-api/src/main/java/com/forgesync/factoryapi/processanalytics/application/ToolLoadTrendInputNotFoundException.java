package com.forgesync.factoryapi.processanalytics.application;

public final class ToolLoadTrendInputNotFoundException extends RuntimeException {
  public ToolLoadTrendInputNotFoundException(String processingRunId, Throwable cause) {
    super("Tool load trend input was not found: " + processingRunId, cause);
  }
}
