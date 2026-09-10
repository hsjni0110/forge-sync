package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessReport;
import java.time.Instant;

public record OperationalEffectivenessProcessingResult(
    String processingRunId,
    Instant createdAt,
    boolean isCreated,
    OperationalEffectivenessReport report) {
  public OperationalEffectivenessProcessingResult asExisting() {
    return isCreated
        ? new OperationalEffectivenessProcessingResult(processingRunId, createdAt, false, report)
        : this;
  }
}
