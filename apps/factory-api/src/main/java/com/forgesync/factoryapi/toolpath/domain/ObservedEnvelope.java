package com.forgesync.factoryapi.toolpath.domain;

import java.util.List;

public record ObservedEnvelope(List<Double> minimumMillimeters, List<Double> maximumMillimeters) {}
