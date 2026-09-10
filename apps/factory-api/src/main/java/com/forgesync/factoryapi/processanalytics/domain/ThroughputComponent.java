package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;

public record ThroughputComponent(
    ComponentStatus status,
    BigDecimal partCount,
    int usedTransitionCount,
    int resetCount,
    int unavailableObservationCount,
    String reason) {}
