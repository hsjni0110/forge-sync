package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateInterval;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.IntervalBoundaryEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.SignalCoverage;
import java.time.Instant;

final class EquipmentStateIntervalResponseMapper {

  static final String SCHEMA_VERSION = "1.0.0";

  EquipmentStateIntervalResponse map(EquipmentStateIntervalProcessingResult result) {
    EquipmentStateIntervalReport report = result.report();
    return new EquipmentStateIntervalResponse(
        SCHEMA_VERSION,
        result.processingRunId(),
        report.machineId(),
        report.replaySessionId().toString(),
        report.throughReplaySequence(),
        report.ruleVersion(),
        report.observedFrom().toString(),
        report.observedTo().toString(),
        report.inputHash(),
        report.inputObservationCount(),
        report.resultHash(),
        result.createdAt().toString(),
        report.coverage().stream().map(EquipmentStateIntervalResponseMapper::mapCoverage).toList(),
        report.intervals().stream()
            .map(EquipmentStateIntervalResponseMapper::mapInterval)
            .toList());
  }

  private static EquipmentStateIntervalResponse.Coverage mapCoverage(SignalCoverage coverage) {
    return new EquipmentStateIntervalResponse.Coverage(
        coverage.signal().name(),
        coverage.closedDuration().toString(),
        coverage.leadingUnobserved().toString(),
        coverage.openSince() == null ? null : coverage.openSince().toString(),
        coverage.intervalCount());
  }

  private static EquipmentStateIntervalResponse.Interval mapInterval(
      EquipmentStateInterval interval) {
    return new EquipmentStateIntervalResponse.Interval(
        interval.signal().name(),
        interval.value(),
        interval.startedAt().toString(),
        instantOrNull(interval.endedAt()),
        interval.duration().map(duration -> duration.toNanos() / 1_000_000_000d).orElse(null),
        mapEvidence(interval.startEvidence()),
        mapEvidence(interval.endEvidence()));
  }

  private static EquipmentStateIntervalResponse.Evidence mapEvidence(
      IntervalBoundaryEvidence evidence) {
    return evidence == null
        ? null
        : new EquipmentStateIntervalResponse.Evidence(
            evidence.replaySequence(),
            evidence.sourceObservedAt().toString(),
            evidence.sourceEventKey());
  }

  private static String instantOrNull(Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
