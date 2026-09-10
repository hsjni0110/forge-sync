package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationalEffectivenessResponse(
    String schemaVersion,
    String processingRunId,
    Instant createdAt,
    String policyVersion,
    String machineId,
    String replaySessionId,
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
    Availability availability,
    Performance performance,
    Throughput throughput,
    Unavailable quality,
    Unavailable compositeOee) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Availability(
      String status,
      BigDecimal percent,
      String sourceProvenance,
      String valueProvenance,
      String formula,
      String reason) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Performance(
      String status,
      BigDecimal percent,
      BigDecimal actualCycleSeconds,
      BigDecimal referenceSeconds,
      String referenceKind,
      String provenance,
      int sampleCount,
      List<String> contributingFeatureSetIds,
      String reason) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Throughput(
      String status,
      BigDecimal partCount,
      int usedTransitionCount,
      int resetCount,
      int unavailableObservationCount,
      String reason) {}

  public record Unavailable(String status, String provenance, String reason) {}
}
