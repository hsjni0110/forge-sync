package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.util.List;

public record PerformanceComponent(
    ComponentStatus status,
    BigDecimal percent,
    BigDecimal actualCycleSeconds,
    BigDecimal referenceSeconds,
    String referenceKind,
    ValueProvenance provenance,
    int sampleCount,
    List<String> contributingFeatureSetIds,
    String reason) {}
