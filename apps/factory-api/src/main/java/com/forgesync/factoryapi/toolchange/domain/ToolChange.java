package com.forgesync.factoryapi.toolchange.domain;

import java.time.Instant;
import java.util.UUID;

public record ToolChange(
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    long fromToolNumber,
    long toToolNumber,
    String sourceDataItemId,
    String sourceSetId,
    String artifactId,
    String rawRecordId,
    String mappingVersion) {}
