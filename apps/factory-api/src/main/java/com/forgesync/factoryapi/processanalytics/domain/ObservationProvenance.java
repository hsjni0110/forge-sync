package com.forgesync.factoryapi.processanalytics.domain;

import java.util.Objects;

public record ObservationProvenance(
    String sourceKind,
    String provider,
    String sourceSetId,
    String artifactId,
    String rawRecordId,
    String mappingVersion,
    String sourceDataItemId) {

  public ObservationProvenance {
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(sourceSetId, "sourceSetId");
    Objects.requireNonNull(artifactId, "artifactId");
    Objects.requireNonNull(rawRecordId, "rawRecordId");
    Objects.requireNonNull(mappingVersion, "mappingVersion");
    Objects.requireNonNull(sourceDataItemId, "sourceDataItemId");
  }
}
