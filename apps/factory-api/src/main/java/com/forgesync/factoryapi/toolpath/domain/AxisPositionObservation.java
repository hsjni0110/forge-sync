package com.forgesync.factoryapi.toolpath.domain;

import java.time.Instant;

public record AxisPositionObservation(
    String axis,
    long replaySequence,
    Instant sourceObservedAt,
    String availability,
    Double value,
    String unit,
    String sourceDataItemId,
    String sourceSetId,
    String artifactId,
    String rawRecordId,
    String mappingVersion) {}
