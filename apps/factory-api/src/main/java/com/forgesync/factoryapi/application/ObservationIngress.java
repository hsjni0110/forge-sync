package com.forgesync.factoryapi.application;

public interface ObservationIngress {

  IngestionResult acceptObservation(ValidatedObservationMessage observation);
}
