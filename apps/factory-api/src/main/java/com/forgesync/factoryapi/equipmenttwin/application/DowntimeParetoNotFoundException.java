package com.forgesync.factoryapi.equipmenttwin.application;

public final class DowntimeParetoNotFoundException extends RuntimeException {
  public DowntimeParetoNotFoundException(String identity) {
    super("Downtime Pareto was not found: " + identity);
  }
}
