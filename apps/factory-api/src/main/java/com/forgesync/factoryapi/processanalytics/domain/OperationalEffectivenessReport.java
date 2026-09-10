package com.forgesync.factoryapi.processanalytics.domain;

import java.time.Instant;
import java.util.UUID;

public record OperationalEffectivenessReport(
    String policyVersion,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    Instant observedFrom,
    Instant observedTo,
    String utilizationProcessingRunId,
    String cycleFeatureProcessingRunId,
    String machiningRunProcessingRunId,
    String targetFeatureSetId,
    String programName,
    String inputHash,
    String resultHash,
    AvailabilityComponent availability,
    PerformanceComponent performance,
    ThroughputComponent throughput,
    UnavailableComponent quality,
    UnavailableComponent compositeOee) {}
