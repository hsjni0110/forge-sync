package com.forgesync.factoryapi.processanalytics.domain;

import java.time.Instant;
import java.util.Objects;

public record BoundaryEvidence(
    String role,
    ProcessSignal signal,
    String sourceEventKey,
    long replaySequence,
    Instant sourceObservedAt,
    ObservationProvenance provenance) {

  public BoundaryEvidence {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(signal, "signal");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(provenance, "provenance");
  }

  public static BoundaryEvidence from(String role, ProcessObservation observation) {
    return new BoundaryEvidence(
        role,
        observation.signal(),
        observation.sourceEventKey(),
        observation.replaySequence(),
        observation.sourceObservedAt(),
        observation.provenance());
  }
}
