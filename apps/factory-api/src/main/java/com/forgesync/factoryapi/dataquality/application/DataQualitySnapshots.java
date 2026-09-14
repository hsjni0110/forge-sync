package com.forgesync.factoryapi.dataquality.application;

import java.time.Instant;

public final class DataQualitySnapshots {
  private DataQualitySnapshots() {}

  public record RuntimeSnapshot(
      long receivedCount,
      long acceptedCount,
      long duplicateCount,
      long outOfOrderCount,
      Instant firstIngestedAt,
      Instant lastIngestedAt) {}

  public record SegmentationSnapshot(
      String processingRunId, int inputObservationCount, int resultCount) {}

  public record FeatureCoverageSnapshot(
      String processingRunId,
      int eligibleRunCount,
      int availableCount,
      int partialCount,
      int missingCount,
      int emptyWindowCount) {}

  public record DerivedProcessSnapshot(
      SegmentationSnapshot segmentation, FeatureCoverageSnapshot featureCoverage) {}

  public record FreshnessSnapshot(
      String value, long ageMillis, long freshMaxAgeMillis, long laggingMaxAgeMillis) {}
}
