package com.forgesync.factoryapi.processanalytics.application;

public final class OperationalEffectivenessNotFoundException extends RuntimeException {
  public OperationalEffectivenessNotFoundException(String id) {
    super("Operational effectiveness processing run not found: " + id);
  }
}
