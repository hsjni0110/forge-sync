package com.forgesync.factoryapi.equipmenttwin.domain;

import java.util.Objects;

public final class ObservationOrderingPolicy {

  public ProjectionDecision decide(
      ObservationOrder candidate, ObservationOrder currentObservation) {
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(currentObservation, "currentObservation");
    return compare(candidate, currentObservation) > 0
        ? ProjectionDecision.PROJECT
        : ProjectionDecision.KEEP_CURRENT;
  }

  private static int compare(ObservationOrder candidate, ObservationOrder currentObservation) {
    if (candidate.replaySessionId().equals(currentObservation.replaySessionId())) {
      int sequenceOrder =
          Long.compare(candidate.replaySequence(), currentObservation.replaySequence());
      if (sequenceOrder != 0) {
        return sequenceOrder;
      }
    }

    int sourceTimeOrder =
        candidate.sourceObservedAt().compareTo(currentObservation.sourceObservedAt());
    if (sourceTimeOrder != 0) {
      return sourceTimeOrder;
    }
    return candidate.sourceEventKey().compareTo(currentObservation.sourceEventKey());
  }
}
