package com.forgesync.factoryapi.toolpath.domain;

import java.util.List;

public record ObservedToolpath(
    ToolpathAvailability availability,
    String reason,
    List<ToolpathPoint> points,
    ObservedEnvelope observedEnvelope) {}
