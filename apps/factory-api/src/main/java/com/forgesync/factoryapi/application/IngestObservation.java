package com.forgesync.factoryapi.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class IngestObservation implements ObservationIngress {

  private final ObservationTransaction observationTransaction;
  private final Clock clock;

  public IngestObservation(ObservationTransaction observationTransaction, Clock clock) {
    this.observationTransaction = Objects.requireNonNull(observationTransaction);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public IngestionResult acceptObservation(ValidatedObservationMessage observation) {
    Instant ingestedAt = clock.instant();
    Instant projectedAt = clock.instant();
    return observationTransaction.storeObservation(observation, ingestedAt, projectedAt);
  }
}
