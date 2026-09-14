package com.forgesync.factoryapi.dataquality.application;

import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import java.time.Instant;

@FunctionalInterface
public interface DataQualityObservationParticipant {
  void record(
      ValidatedObservationMessage observation,
      Instant ingestedAt,
      RuntimeObservationOutcome outcome);

  static DataQualityObservationParticipant noOp() {
    return (observation, ingestedAt, outcome) -> {};
  }
}
