package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.Objects;

public record UtilizationKpiCommand(
    String machineId, String intervalProcessingRunId, String calculationVersion) {

  public UtilizationKpiCommand {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(intervalProcessingRunId, "intervalProcessingRunId");
    Objects.requireNonNull(calculationVersion, "calculationVersion");
  }
}
