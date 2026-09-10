package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessProcessingResult;

final class OperationalEffectivenessResponseMapper {
  OperationalEffectivenessResponse map(OperationalEffectivenessProcessingResult result) {
    var report = result.report();
    return new OperationalEffectivenessResponse(
        "1.0.0",
        result.processingRunId(),
        result.createdAt(),
        report.policyVersion(),
        report.machineId(),
        report.replaySessionId().toString(),
        report.throughReplaySequence(),
        report.observedFrom(),
        report.observedTo(),
        report.utilizationProcessingRunId(),
        report.cycleFeatureProcessingRunId(),
        report.machiningRunProcessingRunId(),
        report.targetFeatureSetId(),
        report.programName(),
        report.inputHash(),
        report.resultHash(),
        availability(report.availability()),
        performance(report.performance()),
        throughput(report.throughput()),
        unavailable(report.quality()),
        unavailable(report.compositeOee()));
  }

  private static OperationalEffectivenessResponse.Availability availability(
      com.forgesync.factoryapi.processanalytics.domain.AvailabilityComponent value) {
    return new OperationalEffectivenessResponse.Availability(
        value.status().name(),
        value.percent(),
        value.sourceProvenance().name(),
        value.valueProvenance().name(),
        value.formula(),
        value.reason());
  }

  private static OperationalEffectivenessResponse.Performance performance(
      com.forgesync.factoryapi.processanalytics.domain.PerformanceComponent value) {
    return new OperationalEffectivenessResponse.Performance(
        value.status().name(),
        value.percent(),
        value.actualCycleSeconds(),
        value.referenceSeconds(),
        value.referenceKind(),
        value.provenance().name(),
        value.sampleCount(),
        value.contributingFeatureSetIds(),
        value.reason());
  }

  private static OperationalEffectivenessResponse.Throughput throughput(
      com.forgesync.factoryapi.processanalytics.domain.ThroughputComponent value) {
    return new OperationalEffectivenessResponse.Throughput(
        value.status().name(),
        value.partCount(),
        value.usedTransitionCount(),
        value.resetCount(),
        value.unavailableObservationCount(),
        value.reason());
  }

  private static OperationalEffectivenessResponse.Unavailable unavailable(
      com.forgesync.factoryapi.processanalytics.domain.UnavailableComponent value) {
    return new OperationalEffectivenessResponse.Unavailable(
        value.status().name(), value.provenance().name(), value.reason());
  }
}
