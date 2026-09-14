package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendReport;

public final class ToolLoadTrendResponseMapper {
  public ToolLoadTrendResponse map(ToolLoadTrendReport report) {
    return new ToolLoadTrendResponse(
        "1.0.0",
        report.policyVersion(),
        report.machineId(),
        report.replaySessionId(),
        report.throughReplaySequence(),
        report.machiningRunProcessingRunId(),
        new ToolLoadTrendResponse.Policy(
            report.policy().minimumRawSamplesPerPoint(),
            report.policy().minimumTrendPoints(),
            report.policy().baselinePointCount(),
            report.policy().minimumCoverageRatio(),
            report.policy().pointFormula(),
            report.policy().coverageFormula(),
            report.policy().deviationFormula(),
            report.policy().slopeFormula()),
        new ToolLoadTrendResponse.Provenance(
            report.provenance().origin(),
            report.provenance().sourceKind(),
            report.provenance().provider(),
            report.provenance().sourceSetId()),
        report.groups().stream().map(ToolLoadTrendResponseMapper::group).toList());
  }

  private static ToolLoadTrendResponse.Group group(ToolLoadTrendReport.Group group) {
    return new ToolLoadTrendResponse.Group(
        group.programName(),
        group.toolNumber(),
        group.componentId(),
        group.sourceDataItemId(),
        group.unit(),
        group.status(),
        group.reasons(),
        group.candidatePointCount(),
        group.eligiblePointCount(),
        group.coverageRatio(),
        group.baselineMedianLoad(),
        group.latestDeviationPercent(),
        group.slopePercentPerPoint(),
        group.points().stream().map(ToolLoadTrendResponseMapper::point).toList());
  }

  private static ToolLoadTrendResponse.Point point(ToolLoadTrendReport.Point point) {
    return new ToolLoadTrendResponse.Point(
        point.machiningRunId(),
        point.status(),
        point.availableSampleCount(),
        point.totalObservationCount(),
        point.medianLoad(),
        point.deviationPercent(),
        evidence(point.firstEvidence()),
        evidence(point.lastEvidence()));
  }

  private static ToolLoadTrendResponse.Evidence evidence(ToolLoadTrendReport.Evidence evidence) {
    return new ToolLoadTrendResponse.Evidence(
        evidence.replaySequence(),
        evidence.sourceObservedAt(),
        evidence.sourceEventKey(),
        evidence.rawRecordId(),
        evidence.sourceDataItemId(),
        evidence.mappingVersion());
  }
}
