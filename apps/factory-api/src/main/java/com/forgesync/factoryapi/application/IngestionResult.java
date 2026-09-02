package com.forgesync.factoryapi.application;

public enum IngestionResult {
  ACCEPTED,
  ACCEPTED_LATE,
  SKIPPED_DUPLICATE;

  public String metricValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
