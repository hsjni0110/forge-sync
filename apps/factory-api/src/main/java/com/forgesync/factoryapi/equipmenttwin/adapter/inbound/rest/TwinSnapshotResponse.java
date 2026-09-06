package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TwinSnapshotResponse(
    String schemaVersion,
    MachineDto machine,
    ConsistencyDto consistency,
    ReplayCursorDto replayCursor,
    StateDto state,
    MetricsDto metrics,
    List<ConditionDto> conditions,
    Map<String, Object> production,
    Map<String, Object> productionResult,
    List<Object> alarms,
    Map<String, Object> maintenance,
    Map<String, Object> intelligence,
    Map<String, Object> quality,
    SpatialDto spatial) {

  public record MachineDto(String machineId) {}

  public record SpatialDto(
      String assetId,
      String sceneNodeId,
      List<Double> position,
      String positionUnit,
      List<Double> rotation,
      String rotationUnit,
      List<Double> scale,
      String provenance) {}

  public record ConsistencyDto(
      String status, long twinVersion, Instant projectedAt, List<String> missingFields) {}

  public record ReplayCursorDto(
      java.util.UUID replaySessionId,
      long replaySequence,
      Instant sourceObservedAt,
      Instant replayPublishedAt,
      long twinVersion) {}

  public record StateDto(
      DerivedStateDto connectivity,
      DerivedStateDto execution,
      DerivedStateDto health,
      FreshnessDto freshness) {}

  public record DerivedStateDto(String value, List<FieldProvenanceDto> provenance) {}

  public record FreshnessDto(
      String value,
      Instant evaluatedAt,
      Instant projectedAt,
      long ageMillis,
      long freshMaxAgeMillis,
      long laggingMaxAgeMillis,
      String basis) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetricsDto(
      List<SpindleSpeedDto> spindleSpeeds,
      List<AxisPositionDto> axisPositions,
      ObservedAngleDto bAxisAngle,
      ObservedIntegerDto toolNumber,
      ObservedTextDto program) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SpindleSpeedDto(
      String availability,
      BigDecimal value,
      String unit,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ObservedAngleDto(
      String availability,
      BigDecimal value,
      String unit,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AxisPositionDto(
      String axis,
      String availability,
      BigDecimal value,
      String unit,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ObservedIntegerDto(
      String availability,
      Long value,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ObservedTextDto(
      String availability,
      String value,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConditionDto(
      String conditionType,
      String level,
      String nativeCode,
      String nativeSeverity,
      String qualifier,
      String message,
      ObservationMetadataDto observation,
      FieldProvenanceDto provenance) {}

  public record ObservationMetadataDto(
      String componentId, Instant sourceObservedAt, Instant projectedAt, long twinVersion) {}

  public record FieldProvenanceDto(
      SourceProvenanceDto source, TransformationProvenanceDto transformation) {}

  public record SourceProvenanceDto(
      String kind, String provider, String sourceSetId, String artifactId) {}

  public record TransformationProvenanceDto(
      String rawRecordId, String mappingVersion, String sourceDataItemId) {}
}
