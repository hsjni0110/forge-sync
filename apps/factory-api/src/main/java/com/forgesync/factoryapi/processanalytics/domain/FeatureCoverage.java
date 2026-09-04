package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;

public record FeatureCoverage(
    BigDecimal coveredSeconds, BigDecimal windowSeconds, BigDecimal ratio) {}
