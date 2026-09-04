package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CycleFeatureExtractorTest {

  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final ObservationProvenance PROVENANCE =
      new ObservationProvenance(
          "REAL",
          "NIST",
          "nist-mazak01-20161005",
          "sha256:" + "a".repeat(64),
          "raw",
          "2.0.0",
          "data-item");

  @Test
  void extractsGoldenTimeWeightedStateAndMetricFeatures() {
    CycleFeature feature =
        new CycleFeatureExtractor()
            .extract(
                completedRun(START, START.plusSeconds(10)),
                List.of(
                    state(1, 0, true, "ACTIVE"),
                    metric(2, 0, true, "0", "REVOLUTION/MINUTE"),
                    metric(3, 2, true, "100", "REVOLUTION/MINUTE"),
                    state(4, 7, true, "FEED_HOLD"),
                    metric(5, 6, true, "200", "REVOLUTION/MINUTE")));

    assertThat(feature.durationSeconds()).isEqualByComparingTo("10.000000000");
    assertThat(feature.cuttingSeconds()).isEqualByComparingTo("7.000000000");
    assertThat(feature.idleSeconds()).isEqualByComparingTo("3.000000000");
    assertThat(feature.stateCoverage().ratio()).isEqualByComparingTo("1.000000");
    MetricFeature rpm = feature.metricFeatures().getFirst();
    assertThat(rpm.mean()).isEqualByComparingTo("120.000000");
    assertThat(rpm.maximum()).isEqualByComparingTo("200");
    assertThat(rpm.populationStandardDeviation()).isEqualByComparingTo("74.833148");
    assertThat(rpm.coverage().ratio()).isEqualByComparingTo("1.000000");
    assertThat(rpm.status()).isEqualTo(FeatureAvailability.AVAILABLE);
  }

  @Test
  void preservesPartialCoverageCarryInUnavailableGapsAndMissingChannels() {
    CycleFeature feature =
        new CycleFeatureExtractor()
            .extract(
                completedRun(START, START.plusSeconds(10)),
                List.of(
                    state(1, -2, true, "ACTIVE"),
                    metric(2, -1, true, "80", "REVOLUTION/MINUTE"),
                    metric(3, 4, false, null, null),
                    metric(4, 8, true, "160", "REVOLUTION/MINUTE")));

    assertThat(feature.cuttingSeconds()).isEqualByComparingTo("10.000000000");
    assertThat(feature.metricFeatures()).hasSize(3);
    MetricFeature rpm = feature.metricFeatures().getFirst();
    assertThat(rpm.status()).isEqualTo(FeatureAvailability.PARTIAL);
    assertThat(rpm.coverage().coveredSeconds()).isEqualByComparingTo("6.000000000");
    assertThat(rpm.coverage().ratio()).isEqualByComparingTo("0.600000");
    assertThat(feature.metricFeatures().stream().filter(item -> item.metric() == CycleMetric.LOAD))
        .singleElement()
        .extracting(MetricFeature::status)
        .isEqualTo(FeatureAvailability.MISSING);
    assertThat(
            feature.metricFeatures().stream()
                .filter(item -> item.metric() == CycleMetric.PATH_FEEDRATE))
        .singleElement()
        .extracting(MetricFeature::status)
        .isEqualTo(FeatureAvailability.MISSING);
  }

  @Test
  void marksZeroDurationWindowAsEmptyAndRejectsUnitMismatch() {
    CycleFeature empty = new CycleFeatureExtractor().extract(completedRun(START, START), List.of());

    assertThat(empty.status()).isEqualTo(FeatureAvailability.EMPTY_WINDOW);
    assertThat(empty.durationSeconds()).isEqualByComparingTo("0.000000000");
    assertThat(empty.cuttingSeconds()).isNull();
    assertThat(empty.idleSeconds()).isNull();
    assertThat(empty.stateCoverage().ratio()).isNull();
    assertThat(empty.metricFeatures()).allMatch(feature -> feature.coverage().ratio() == null);

    assertThatThrownBy(
            () ->
                new CycleFeatureExtractor()
                    .extract(
                        completedRun(START, START.plusSeconds(10)),
                        List.of(
                            metric(1, 0, true, "100", "REVOLUTION/MINUTE"),
                            metric(2, 5, true, "200", "PERCENT"))))
        .isInstanceOf(CycleFeatureUnitMismatchException.class)
        .hasMessageContaining("CYCLE_FEATURE_UNIT_MISMATCH");
  }

  @Test
  void leavesUnavailableExecutionTimeUncoveredInsteadOfCallingItIdle() {
    CycleFeature feature =
        new CycleFeatureExtractor()
            .extract(
                completedRun(START, START.plusSeconds(10)),
                List.of(
                    state(1, 0, true, "ACTIVE"),
                    state(2, 4, false, null),
                    state(3, 7, true, "ACTIVE")));

    assertThat(feature.cuttingSeconds()).isEqualByComparingTo("7.000000000");
    assertThat(feature.idleSeconds()).isEqualByComparingTo("0.000000000");
    assertThat(feature.stateCoverage().coveredSeconds()).isEqualByComparingTo("7.000000000");
    assertThat(feature.stateCoverage().ratio()).isEqualByComparingTo("0.700000");
  }

  private static MachiningRun completedRun(Instant startedAt, Instant endedAt) {
    ObservationRange range =
        new ObservationRange(SESSION, 1, 9, startedAt, endedAt, "first", "last");
    BoundaryEvidence evidence =
        new BoundaryEvidence("START", ProcessSignal.EXECUTION, "first", 1, startedAt, PROVENANCE);
    return new MachiningRun(
        "sha256:" + "b".repeat(64),
        "sha256:" + "c".repeat(64),
        "1.0.0",
        "Mazak01",
        MachiningRunStatus.COMPLETED,
        "155",
        startedAt,
        endedAt,
        SegmentationConfidence.HIGH,
        List.of("EXECUTION_BOUNDARY"),
        range,
        evidence,
        evidence,
        List.of(),
        "sha256:" + "d".repeat(64));
  }

  private static CycleObservation state(
      long sequence, long offset, boolean available, String value) {
    return new CycleObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(offset),
        "state-" + sequence,
        CycleSignal.EXECUTION,
        "Mazak01-controller",
        "execution",
        null,
        available,
        value,
        null,
        PROVENANCE);
  }

  private static CycleObservation metric(
      long sequence, long offset, boolean available, String value, String unit) {
    return new CycleObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(offset),
        "metric-" + sequence,
        CycleSignal.SPINDLE_SPEED,
        "Mazak01-spindle",
        "spindle_speed",
        unit,
        available,
        null,
        value == null ? null : new BigDecimal(value),
        PROVENANCE);
  }
}
