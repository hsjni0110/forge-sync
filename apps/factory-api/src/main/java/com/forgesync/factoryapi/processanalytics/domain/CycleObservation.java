package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CycleObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    CycleSignal signal,
    String componentId,
    String sourceDataItemId,
    String unit,
    boolean isAvailable,
    String textValue,
    BigDecimal numericValue,
    ObservationProvenance provenance) {

  public CycleObservation {
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(replaySessionId);
    Objects.requireNonNull(sourceObservedAt);
    Objects.requireNonNull(sourceEventKey);
    Objects.requireNonNull(signal);
    Objects.requireNonNull(componentId);
    Objects.requireNonNull(sourceDataItemId);
    Objects.requireNonNull(provenance);
    if (replaySequence < 0)
      throw new IllegalArgumentException("replaySequence must not be negative");
    if (!isAvailable && (textValue != null || numericValue != null)) {
      throw new IllegalArgumentException("Unavailable observations cannot contain a value");
    }
    if (isAvailable && signal.isMetric() && (numericValue == null || unit == null)) {
      throw new IllegalArgumentException("Available metric observations require value and unit");
    }
    if (isAvailable && !signal.isMetric() && textValue == null) {
      throw new IllegalArgumentException("Available state observations require a value");
    }
  }
}
