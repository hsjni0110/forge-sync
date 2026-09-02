package com.forgesync.factoryapi.application;

import java.time.Instant;

public interface ObservationTransaction {

  IngestionResult storeObservation(
      ValidatedObservationMessage observation, Instant ingestedAt, Instant projectedAt);
}
