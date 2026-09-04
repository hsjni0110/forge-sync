package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MachiningRunResponse(
    String schemaVersion,
    String processingRunId,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    String segmentationRuleVersion,
    String inputHash,
    int inputObservationCount,
    String resultHash,
    Instant createdAt,
    List<RunDto> machiningRuns) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RunDto(
      String machiningRunId,
      String processingRunId,
      String segmentationRuleVersion,
      String machineId,
      String status,
      String programName,
      Instant startedAt,
      Instant endedAt,
      ConfidenceDto confidence,
      ObservationRangeDto sourceObservationRange,
      EvidenceDto startEvidence,
      EvidenceDto endEvidence,
      List<EvidenceDto> supportingEvidence,
      ProvenanceDto provenance,
      String resultHash) {}

  public record ConfidenceDto(String level, List<String> reasons) {}

  public record ObservationRangeDto(
      UUID replaySessionId,
      long firstReplaySequence,
      long lastReplaySequence,
      Instant firstSourceObservedAt,
      Instant lastSourceObservedAt,
      String firstSourceEventKey,
      String lastSourceEventKey) {}

  public record EvidenceDto(
      String role,
      String signal,
      String sourceEventKey,
      long replaySequence,
      Instant sourceObservedAt,
      SourceDto source,
      TransformationDto transformation) {}

  public record ProvenanceDto(
      String origin, SourceDto source, ProcessingTransformationDto transformation) {}

  public record SourceDto(String kind, String provider, String sourceSetId, String artifactId) {}

  public record TransformationDto(
      String rawRecordId, String mappingVersion, String sourceDataItemId) {}

  public record ProcessingTransformationDto(
      String processingRunId, String segmentationRuleVersion) {}
}
