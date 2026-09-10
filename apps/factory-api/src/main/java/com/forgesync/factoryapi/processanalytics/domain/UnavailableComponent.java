package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;

public record UnavailableComponent(
    ComponentStatus status, BigDecimal percent, ValueProvenance provenance, String reason) {}
