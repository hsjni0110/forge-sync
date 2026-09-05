package com.forgesync.factoryapi.processanalytics.domain;

public record CycleFeatureContext(
    String cycleFeatureSetId, String programName, CycleFeature cycleFeature) {}
