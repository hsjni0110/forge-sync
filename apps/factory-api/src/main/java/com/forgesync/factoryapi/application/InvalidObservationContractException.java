package com.forgesync.factoryapi.application;

import java.util.List;

public final class InvalidObservationContractException extends RuntimeException {

  public static final String ERROR_CODE = "INVALID_OBSERVATION_CONTRACT";

  private final List<String> violations;

  public InvalidObservationContractException(List<String> violations) {
    super(ERROR_CODE);
    this.violations = List.copyOf(violations);
  }

  public List<String> violations() {
    return violations;
  }
}
