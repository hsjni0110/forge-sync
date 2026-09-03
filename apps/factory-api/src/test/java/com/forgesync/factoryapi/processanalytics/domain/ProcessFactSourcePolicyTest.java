package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProcessFactSourcePolicyTest {

  private final ProcessFactSourcePolicy policy = new ProcessFactSourcePolicy();

  @Test
  void classifiesCanonicalObservationsAsDerivedProcessFacts() {
    assertThat(policy.classify(ProcessInputKind.CANONICAL_OBSERVATION))
        .isEqualTo(ProcessFactOrigin.DERIVED);
  }

  @Test
  void rejectsSimulatedOperationsAndReferenceHealth() {
    assertThatThrownBy(() -> policy.classify(ProcessInputKind.SIMULATED_OPERATION))
        .isInstanceOf(UnsupportedProcessSourceException.class)
        .hasMessage("Unsupported Process Analytics input: SIMULATED_OPERATION");
    assertThatThrownBy(() -> policy.classify(ProcessInputKind.REFERENCE_HEALTH))
        .isInstanceOf(UnsupportedProcessSourceException.class)
        .hasMessage("Unsupported Process Analytics input: REFERENCE_HEALTH");
  }

  @Test
  void rejectsAnAbsentInputKind() {
    assertThatThrownBy(() -> policy.classify(null))
        .isInstanceOf(UnsupportedProcessSourceException.class)
        .hasMessage("Process input kind is required");
  }
}
