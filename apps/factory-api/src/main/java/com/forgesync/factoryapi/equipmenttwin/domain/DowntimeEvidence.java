package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;

public record DowntimeEvidence(
    DowntimeEvidenceKind kind,
    String signal,
    String value,
    Instant sourceObservedAt,
    long replaySequence,
    String sourceEventKey,
    String componentId,
    String conditionType,
    String level,
    String nativeCode,
    String message) {}
