package com.forgesync.factoryapi.equipmenttwin.application;

public final class UtilizationKpiNotFoundException extends RuntimeException {
  public UtilizationKpiNotFoundException(String reference) {
    super("No utilization KPI processing for " + reference);
  }
}
