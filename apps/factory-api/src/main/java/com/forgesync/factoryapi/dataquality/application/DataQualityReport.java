package com.forgesync.factoryapi.dataquality.application;

import com.forgesync.factoryapi.dataquality.application.SourceQualityEvidence.UnmappedDataItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataQualityReport(
    String schemaVersion,
    String machineId,
    String sourceSetId,
    UUID replaySessionId,
    Long throughReplaySequence,
    Instant evaluatedAt,
    String overallGrade,
    Dimensions dimensions,
    RuntimeQuality runtime,
    DerivedProcessQuality derivedProcess,
    List<UnmappedDataItem> unmappedDataItems,
    Evidence evidence) {

  public record Dimensions(
      LayeredDimension validity,
      Measurement completeness,
      LayeredDimension ordering,
      LayeredDimension duplication,
      Freshness freshness,
      SemanticCoverage semanticCoverage) {}

  public record Measurement(
      String status,
      long numerator,
      long denominator,
      BigDecimal ratio,
      String grade,
      String basis,
      String reason) {}

  public record LayeredDimension(Measurement sourceProfile, Measurement runtime) {}

  public record Freshness(
      String status,
      String value,
      Long ageMillis,
      Long freshMaxAgeMillis,
      Long laggingMaxAgeMillis,
      String basis,
      String reason) {}

  public record SemanticCoverage(
      String status,
      long numerator,
      long denominator,
      BigDecimal ratio,
      String grade,
      String basis,
      String reason,
      String mappingVersion,
      String processingRunId) {}

  public record RuntimeQuality(
      String status,
      long receivedCount,
      long acceptedCount,
      long duplicateCount,
      long outOfOrderCount,
      Instant firstIngestedAt,
      Instant lastIngestedAt,
      String scope) {}

  public record DerivedProcessQuality(
      DerivedMeasurement segmentation, FeatureCoverage featureCoverage) {}

  public record DerivedMeasurement(
      String status,
      String processingRunId,
      int inputObservationCount,
      int resultCount,
      String reason) {}

  public record FeatureCoverage(
      String status,
      String processingRunId,
      int eligibleRunCount,
      int availableCount,
      int partialCount,
      int missingCount,
      int emptyWindowCount,
      String reason) {}

  public record Evidence(
      String sourceProfileProcessingRunId,
      String semanticMappingProcessingRunId,
      String rawArtifactId,
      String mappingVersion,
      String sourceHref) {}
}
