package com.forgesync.factoryapi.toolchange.adapter.inbound.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ToolChangeTimelineResponse(
    String schemaVersion,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    List<ToolChangeItem> toolChanges) {
  public record ToolChangeItem(
      long replaySequence,
      Instant sourceObservedAt,
      long fromToolNumber,
      long toToolNumber,
      Provenance provenance) {}

  public record Provenance(Source source, Transformation transformation) {}

  public record Source(String kind, String provider, String sourceSetId, String artifactId) {}

  public record Transformation(
      String rawRecordId, String mappingVersion, String sourceDataItemId) {}
}
