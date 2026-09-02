package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.Objects;

public record FieldProvenance(
    String sourceKind,
    String provider,
    String sourceSetId,
    String artifactId,
    String rawRecordId,
    String mappingVersion,
    String sourceDataItemId) {

  public FieldProvenance {
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(sourceSetId, "sourceSetId");
    Objects.requireNonNull(artifactId, "artifactId");
    Objects.requireNonNull(rawRecordId, "rawRecordId");
    Objects.requireNonNull(mappingVersion, "mappingVersion");
    Objects.requireNonNull(sourceDataItemId, "sourceDataItemId");
  }
}
