package com.forgesync.factoryapi.processanalytics.domain;

public final class CycleFeatureUnitMismatchException extends RuntimeException {
  public CycleFeatureUnitMismatchException(String channel) {
    super("CYCLE_FEATURE_UNIT_MISMATCH: " + channel);
  }
}
