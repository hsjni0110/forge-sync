package com.forgesync.factoryapi.processanalytics.domain;

/** Prevents simulated production and reference-health data from becoming observed process facts. */
public final class ProcessFactSourcePolicy {

  public ProcessFactOrigin classify(ProcessInputKind inputKind) {
    if (inputKind == null) {
      throw new UnsupportedProcessSourceException("Process input kind is required");
    }
    if (inputKind != ProcessInputKind.CANONICAL_OBSERVATION) {
      throw new UnsupportedProcessSourceException(
          "Unsupported Process Analytics input: " + inputKind);
    }
    return ProcessFactOrigin.DERIVED;
  }
}
