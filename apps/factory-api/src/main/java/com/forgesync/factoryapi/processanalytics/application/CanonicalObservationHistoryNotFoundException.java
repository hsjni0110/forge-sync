package com.forgesync.factoryapi.processanalytics.application;

public final class CanonicalObservationHistoryNotFoundException extends RuntimeException {
  public CanonicalObservationHistoryNotFoundException(String machineId) {
    super("Canonical Observation history not found for machine " + machineId);
  }
}
