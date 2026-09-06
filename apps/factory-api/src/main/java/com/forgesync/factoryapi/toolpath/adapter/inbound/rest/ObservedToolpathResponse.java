package com.forgesync.factoryapi.toolpath.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservedToolpathResponse(
    String schemaVersion,
    String machineId,
    UUID replaySessionId,
    long startSequence,
    long endSequence,
    String availability,
    String reason,
    String classification,
    List<Point> points,
    Envelope observedEnvelope) {
  public record Point(
      long replaySequence,
      Instant sourceObservedAt,
      List<Double> coordinatesMillimeters,
      List<SourceObservation> sourceObservations) {}

  public record SourceObservation(
      String axis,
      long replaySequence,
      Instant sourceObservedAt,
      String sourceDataItemId,
      String sourceSetId,
      String artifactId,
      String rawRecordId,
      String mappingVersion) {}

  public record Envelope(List<Double> minimumMillimeters, List<Double> maximumMillimeters) {}
}
