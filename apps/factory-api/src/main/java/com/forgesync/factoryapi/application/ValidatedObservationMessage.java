package com.forgesync.factoryapi.application;

import java.util.Objects;

public record ValidatedObservationMessage(
    String observationJson, String machineId, String replaySessionId, String sourceEventKey) {

  public ValidatedObservationMessage {
    Objects.requireNonNull(observationJson, "observationJson");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
  }
}
