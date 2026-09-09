package com.forgesync.factoryapi.equipmenttwin.domain;

public record CounterUtilization(
    CounterDelta totalDelta,
    CounterDelta automaticDelta,
    CounterDelta cuttingDelta,
    CounterRatio automaticRatio,
    CounterRatio cuttingRatio) {}
