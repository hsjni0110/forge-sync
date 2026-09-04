package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProcessObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    ProcessSignal signal,
    boolean isAvailable,
    String textValue,
    BigDecimal numericValue,
    ObservationProvenance provenance) {

  public ProcessObservation {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(signal, "signal");
    Objects.requireNonNull(provenance, "provenance");
    if (!isAvailable && (textValue != null || numericValue != null)) {
      throw new IllegalArgumentException("Unavailable observations cannot contain a value");
    }
    if (signal == ProcessSignal.SPINDLE_SPEED && isAvailable && numericValue == null) {
      throw new IllegalArgumentException("Available spindle observations require a numeric value");
    }
    if (signal != ProcessSignal.SPINDLE_SPEED && isAvailable && textValue == null) {
      throw new IllegalArgumentException("Available event observations require a text value");
    }
  }
}
