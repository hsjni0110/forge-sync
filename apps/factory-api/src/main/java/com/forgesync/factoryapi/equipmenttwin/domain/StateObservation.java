package com.forgesync.factoryapi.equipmenttwin.domain;

import java.util.Objects;

public record StateObservation(
    StateObservationKind kind,
    String semanticType,
    ObservationAvailability availability,
    String value) {

  public StateObservation {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(semanticType, "semanticType");
    if (semanticType.isBlank()) {
      throw new IllegalArgumentException("semanticType must not be blank");
    }
    Objects.requireNonNull(availability, "availability");
    if (availability == ObservationAvailability.AVAILABLE) {
      Objects.requireNonNull(value, "value");
      if (value.isBlank()) {
        throw new IllegalArgumentException("available value must not be blank");
      }
    } else if (value != null) {
      throw new IllegalArgumentException("unavailable observation must not have a value");
    }
  }
}
