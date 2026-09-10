package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;

public record AvailabilityComponent(
    ComponentStatus status,
    BigDecimal percent,
    ValueProvenance sourceProvenance,
    ValueProvenance valueProvenance,
    String formula,
    String reason) {}
