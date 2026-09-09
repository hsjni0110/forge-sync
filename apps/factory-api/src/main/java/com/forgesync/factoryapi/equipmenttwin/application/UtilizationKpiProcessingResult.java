package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiReport;
import java.time.Instant;

public record UtilizationKpiProcessingResult(
    String processingRunId, Instant createdAt, boolean isCreated, UtilizationKpiReport report) {

  public UtilizationKpiProcessingResult asExisting() {
    return isCreated
        ? new UtilizationKpiProcessingResult(processingRunId, createdAt, false, report)
        : this;
  }
}
