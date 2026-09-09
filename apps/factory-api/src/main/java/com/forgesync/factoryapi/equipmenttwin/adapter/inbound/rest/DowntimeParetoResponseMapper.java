package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoEntry;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import com.forgesync.factoryapi.equipmenttwin.domain.IntervalBoundaryEvidence;
import java.math.BigDecimal;

final class DowntimeParetoResponseMapper {
  DowntimeParetoResponse map(DowntimeParetoProcessingResult processing) {
    DowntimeParetoReport report = processing.report();
    return new DowntimeParetoResponse(
        "1.0.0",
        processing.processingRunId(),
        report.machineId(),
        report.replaySessionId().toString(),
        report.throughReplaySequence(),
        report.ruleVersion(),
        report.utilizationProcessingRunId(),
        report.intervalProcessingRunId(),
        report.observedFrom().toString(),
        report.observedTo().toString(),
        report.totalDowntimeSeconds(),
        report.inputHash(),
        report.resultHash(),
        processing.createdAt().toString(),
        report.entries().stream().map(DowntimeParetoResponseMapper::mapEntry).toList());
  }

  private static DowntimeParetoResponse.Entry mapEntry(DowntimeParetoEntry entry) {
    return new DowntimeParetoResponse.Entry(
        entry.rank(),
        entry.state().name(),
        entry.startedAt().toString(),
        entry.endedAt().toString(),
        BigDecimal.valueOf(entry.duration().toNanos(), 9),
        entry.ratioPercent(),
        entry.cumulativeRatioPercent(),
        mapBoundary(entry.startEvidence()),
        mapBoundary(entry.endEvidence()),
        entry.classification().name(),
        entry.evidence().stream().map(DowntimeParetoResponseMapper::mapEvidence).toList());
  }

  private static DowntimeParetoResponse.BoundaryEvidence mapBoundary(
      IntervalBoundaryEvidence evidence) {
    return new DowntimeParetoResponse.BoundaryEvidence(
        evidence.replaySequence(),
        evidence.sourceObservedAt().toString(),
        evidence.sourceEventKey());
  }

  private static DowntimeParetoResponse.Evidence mapEvidence(DowntimeEvidence evidence) {
    return new DowntimeParetoResponse.Evidence(
        evidence.kind().name(),
        evidence.signal(),
        evidence.value(),
        evidence.sourceObservedAt().toString(),
        evidence.replaySequence(),
        evidence.sourceEventKey(),
        evidence.componentId(),
        evidence.conditionType(),
        evidence.level(),
        evidence.nativeCode(),
        evidence.message());
  }
}
