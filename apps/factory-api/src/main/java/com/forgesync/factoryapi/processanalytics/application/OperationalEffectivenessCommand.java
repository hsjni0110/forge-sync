package com.forgesync.factoryapi.processanalytics.application;

import java.math.BigDecimal;
import java.util.Map;

public record OperationalEffectivenessCommand(
    String machineId,
    String utilizationProcessingRunId,
    String cycleFeatureProcessingRunId,
    String policyVersion,
    Map<String, BigDecimal> assumedIdealCycleSecondsByProgram) {
  public OperationalEffectivenessCommand {
    assumedIdealCycleSecondsByProgram =
        assumedIdealCycleSecondsByProgram == null
            ? Map.of()
            : Map.copyOf(assumedIdealCycleSecondsByProgram);
  }
}
