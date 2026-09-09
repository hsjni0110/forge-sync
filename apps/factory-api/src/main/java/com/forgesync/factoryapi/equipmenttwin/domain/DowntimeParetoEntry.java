package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record DowntimeParetoEntry(
    int rank,
    UtilizationState state,
    Instant startedAt,
    Instant endedAt,
    Duration duration,
    BigDecimal ratioPercent,
    BigDecimal cumulativeRatioPercent,
    IntervalBoundaryEvidence startEvidence,
    IntervalBoundaryEvidence endEvidence,
    DowntimeReasonClassification classification,
    List<DowntimeEvidence> evidence) {}
