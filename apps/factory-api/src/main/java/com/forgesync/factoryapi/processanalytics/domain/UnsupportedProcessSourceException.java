package com.forgesync.factoryapi.processanalytics.domain;

/** Raised when a foreign Context's fact is presented as Process Analytics source data. */
public final class UnsupportedProcessSourceException extends IllegalArgumentException {

  public UnsupportedProcessSourceException(String message) {
    super(message);
  }
}
