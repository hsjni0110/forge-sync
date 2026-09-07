package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import java.time.Instant;
import java.util.Objects;

public record EquipmentStateIntervalProcessingResult(
    String processingRunId,
    Instant createdAt,
    boolean isCreated,
    EquipmentStateIntervalReport report) {

  public EquipmentStateIntervalProcessingResult {
    Objects.requireNonNull(processingRunId, "processingRunId");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(report, "report");
  }

  public EquipmentStateIntervalProcessingResult asExisting() {
    return new EquipmentStateIntervalProcessingResult(processingRunId, createdAt, false, report);
  }
}
