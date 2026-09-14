package com.forgesync.factoryapi.dataquality.application;

import com.forgesync.factoryapi.dataquality.application.DataQualityReport.DerivedMeasurement;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.DerivedProcessQuality;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.Dimensions;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.Evidence;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.FeatureCoverage;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.Freshness;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.LayeredDimension;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.Measurement;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.RuntimeQuality;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport.SemanticCoverage;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.DerivedProcessSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.FeatureCoverageSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.FreshnessSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.RuntimeSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.SegmentationSnapshot;
import com.forgesync.factoryapi.dataquality.domain.DataQualityPolicy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class DataQualityReportAssembler {
  private static final String NOT_EVALUATED = "NOT_EVALUATED";
  private static final String MEASURED = "MEASURED";

  public DataQualityReport assembleSource(SourceQualityEvidence source, Instant evaluatedAt) {
    return assemble(
        source, null, null, Optional.empty(), Optional.empty(), Optional.empty(), evaluatedAt);
  }

  public DataQualityReport assemble(
      SourceQualityEvidence source,
      UUID replaySessionId,
      Long throughReplaySequence,
      Optional<RuntimeSnapshot> runtime,
      Optional<DerivedProcessSnapshot> derived,
      Optional<FreshnessSnapshot> freshness,
      Instant evaluatedAt) {
    Measurement sourceValidity =
        measured(
            source.totalRecords() - source.invalidRecords(),
            source.totalRecords(),
            "schema-and-mapping-valid records / source records");
    Measurement runtimeValidity =
        unavailable(
            "contract rejections attributable to a trusted replay identity",
            "Rejected payload identity is not trusted for per-session aggregation.");
    Measurement completeness =
        unavailable(
            "received records / expected records",
            "The source declares no expected delivery cadence or record count.");
    Measurement sourceOrdering =
        measured(
            source.totalRecords() - source.orderingAnomalyCount(),
            source.totalRecords(),
            "source records preserving declared timestamp order / source records");
    Measurement runtimeOrdering =
        runtime
            .map(
                item ->
                    measured(
                        item.acceptedCount() - item.outOfOrderCount(),
                        item.acceptedCount(),
                        "accepted observations not arriving behind the session watermark / accepted observations"))
            .orElseGet(
                () ->
                    unavailable(
                        "accepted observations preserving replay order / accepted observations",
                        "No replay-scoped runtime observations are available."));
    Measurement sourceDuplication =
        unavailable(
            "unique source identities / source records",
            "Raw records do not declare replay delivery identity.");
    Measurement runtimeDuplication =
        runtime
            .map(
                item ->
                    measured(
                        item.receivedCount() - item.duplicateCount(),
                        item.receivedCount(),
                        "non-duplicate deliveries / received deliveries"))
            .orElseGet(
                () ->
                    unavailable(
                        "non-duplicate deliveries / received deliveries",
                        "No replay-scoped runtime observations are available."));
    var semantic = DataQualityPolicy.measure(source.mappedRecords(), source.parsedRecords());
    Dimensions dimensions =
        new Dimensions(
            new LayeredDimension(sourceValidity, runtimeValidity),
            completeness,
            new LayeredDimension(sourceOrdering, runtimeOrdering),
            new LayeredDimension(sourceDuplication, runtimeDuplication),
            freshness
                .map(DataQualityReportAssembler::freshness)
                .orElseGet(DataQualityReportAssembler::unavailableFreshness),
            new SemanticCoverage(
                semantic.status(),
                semantic.numerator(),
                semantic.denominator(),
                semantic.ratio(),
                null,
                "mapped records / syntactically parsed records",
                null,
                source.mappingVersion(),
                source.semanticMappingProcessingRunId()));
    return new DataQualityReport(
        "1.0.0",
        source.machineId(),
        source.sourceSetId(),
        replaySessionId,
        throughReplaySequence,
        evaluatedAt,
        null,
        dimensions,
        runtime
            .map(DataQualityReportAssembler::runtime)
            .orElseGet(DataQualityReportAssembler::unavailableRuntime),
        derived
            .map(DataQualityReportAssembler::derived)
            .orElseGet(DataQualityReportAssembler::unavailableDerived),
        source.unmappedDataItems(),
        new Evidence(
            source.sourceProfileProcessingRunId(),
            source.semanticMappingProcessingRunId(),
            source.rawArtifactId(),
            source.mappingVersion(),
            source.sourceHref()));
  }

  private static Measurement measured(long numerator, long denominator, String basis) {
    var measurement = DataQualityPolicy.measure(numerator, denominator);
    return new Measurement(
        measurement.status(), numerator, denominator, measurement.ratio(), null, basis, null);
  }

  private static Measurement unavailable(String basis, String reason) {
    return new Measurement(NOT_EVALUATED, 0, 0, null, null, basis, reason);
  }

  private static Freshness freshness(FreshnessSnapshot item) {
    return new Freshness(
        MEASURED,
        item.value(),
        item.ageMillis(),
        item.freshMaxAgeMillis(),
        item.laggingMaxAgeMillis(),
        "PROJECTED_AT_WALL_CLOCK",
        null);
  }

  private static Freshness unavailableFreshness() {
    return new Freshness(
        NOT_EVALUATED,
        null,
        null,
        null,
        null,
        "PROJECTED_AT_WALL_CLOCK",
        "No projected Twin exists for this machine.");
  }

  private static RuntimeQuality runtime(RuntimeSnapshot item) {
    return new RuntimeQuality(
        MEASURED,
        item.receivedCount(),
        item.acceptedCount(),
        item.duplicateCount(),
        item.outOfOrderCount(),
        item.firstIngestedAt(),
        item.lastIngestedAt(),
        "REPLAY_SESSION_THROUGH_SEQUENCE");
  }

  private static RuntimeQuality unavailableRuntime() {
    return new RuntimeQuality(
        NOT_EVALUATED, 0, 0, 0, 0, null, null, "REPLAY_SESSION_THROUGH_SEQUENCE");
  }

  private static DerivedProcessQuality derived(DerivedProcessSnapshot item) {
    return new DerivedProcessQuality(
        segmentation(item.segmentation()), featureCoverage(item.featureCoverage()));
  }

  private static DerivedMeasurement segmentation(SegmentationSnapshot item) {
    if (item == null) return unavailableSegmentation();
    return new DerivedMeasurement(
        MEASURED, item.processingRunId(), item.inputObservationCount(), item.resultCount(), null);
  }

  private static FeatureCoverage featureCoverage(FeatureCoverageSnapshot item) {
    if (item == null) return unavailableFeatureCoverage();
    return new FeatureCoverage(
        MEASURED,
        item.processingRunId(),
        item.eligibleRunCount(),
        item.availableCount(),
        item.partialCount(),
        item.missingCount(),
        item.emptyWindowCount(),
        null);
  }

  private static DerivedProcessQuality unavailableDerived() {
    return new DerivedProcessQuality(unavailableSegmentation(), unavailableFeatureCoverage());
  }

  private static DerivedMeasurement unavailableSegmentation() {
    return new DerivedMeasurement(
        NOT_EVALUATED, null, 0, 0, "No processing run exists for this replay scope.");
  }

  private static FeatureCoverage unavailableFeatureCoverage() {
    return new FeatureCoverage(
        NOT_EVALUATED,
        null,
        0,
        0,
        0,
        0,
        0,
        "No feature processing run exists for this replay scope.");
  }
}
