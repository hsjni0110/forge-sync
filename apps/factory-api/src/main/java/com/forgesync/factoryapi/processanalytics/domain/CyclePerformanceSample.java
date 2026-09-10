package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record CyclePerformanceSample(
    String featureSetId, String programName, Instant startedAt, BigDecimal durationSeconds) {}
