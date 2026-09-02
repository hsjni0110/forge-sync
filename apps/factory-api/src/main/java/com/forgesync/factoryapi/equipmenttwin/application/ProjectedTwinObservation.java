package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservationKind;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ProjectedTwinObservation(
    StateObservationKind kind,
    String semanticType,
    ObservationAvailability availability,
    BigDecimal numericValue,
    String textValue,
    Long integerValue,
    String unit,
    String componentId,
    Instant sourceObservedAt,
    Instant projectedAt,
    TwinVersion twinVersion,
    FieldProvenance provenance,
    String nativeCode,
    String nativeSeverity,
    String qualifier,
    String message) {

  public ProjectedTwinObservation {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(semanticType, "semanticType");
    Objects.requireNonNull(availability, "availability");
    Objects.requireNonNull(componentId, "componentId");
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(projectedAt, "projectedAt");
    Objects.requireNonNull(twinVersion, "twinVersion");
    Objects.requireNonNull(provenance, "provenance");
  }
}
