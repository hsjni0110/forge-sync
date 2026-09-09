package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DowntimeParetoReport(
    String ruleVersion,
    String utilizationProcessingRunId,
    String intervalProcessingRunId,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    Instant observedFrom,
    Instant observedTo,
    BigDecimal totalDowntimeSeconds,
    String inputHash,
    String resultHash,
    List<DowntimeParetoEntry> entries) {}
