package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import java.time.Instant;

public record DowntimeParetoProcessingResult(
    String processingRunId, Instant createdAt, boolean isCreated, DowntimeParetoReport report) {
  public DowntimeParetoProcessingResult asExisting() {
    return isCreated
        ? new DowntimeParetoProcessingResult(processingRunId, createdAt, false, report)
        : this;
  }
}
