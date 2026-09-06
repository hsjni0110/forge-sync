package com.forgesync.factoryapi.toolpath.domain;

import java.time.Instant;
import java.util.List;

public record ToolpathPoint(
    long replaySequence,
    Instant sourceObservedAt,
    List<Double> coordinatesMillimeters,
    List<AxisPositionObservation> sourceObservations) {}
