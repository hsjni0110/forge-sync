package com.forgesync.factoryapi.application;

public interface ObservationIngress {

  void acceptObservation(ValidatedObservationMessage observation);
}
