package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UtilizationKpiReport(
    String calculationVersion,
    String intervalProcessingRunId,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    Instant observedFrom,
    Instant observedTo,
    String inputHash,
    String resultHash,
    StateUtilization stateUtilization,
    CounterUtilization counterUtilization,
    UtilizationComparison comparison) {

  public UtilizationKpiReport {
    Objects.requireNonNull(calculationVersion, "calculationVersion");
    Objects.requireNonNull(intervalProcessingRunId, "intervalProcessingRunId");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(observedFrom, "observedFrom");
    Objects.requireNonNull(observedTo, "observedTo");
    Objects.requireNonNull(inputHash, "inputHash");
    Objects.requireNonNull(resultHash, "resultHash");
    Objects.requireNonNull(stateUtilization, "stateUtilization");
    Objects.requireNonNull(counterUtilization, "counterUtilization");
    Objects.requireNonNull(comparison, "comparison");
  }
}
