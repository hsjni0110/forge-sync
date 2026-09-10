package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationalEffectivenessPolicyTest {
  private static final Instant START = Instant.parse("2016-10-05T10:00:00Z");

  @Test
  void leavesPerformanceNumericValueAbsentWhenEarlierBaselineHasFewerThanFiveSamples() {
    var result =
        new OperationalEffectivenessPolicy()
            .performance(
                "target", "P1", seconds("60"), List.of(sample("a", "60"), sample("b", "62")), null);

    assertThat(result.status()).isEqualTo(ComponentStatus.UNAVAILABLE);
    assertThat(result.percent()).isNull();
    assertThat(result.reason()).isEqualTo("MINIMUM_SAMPLE_COUNT_NOT_MET");
  }

  @Test
  void derivesPerformanceFromEarlierSameProgramMedianWithoutClampingAboveOneHundred() {
    var result =
        new OperationalEffectivenessPolicy()
            .performance(
                "target",
                "P1",
                seconds("50"),
                List.of(
                    sample("a", "58"),
                    sample("b", "59"),
                    sample("c", "60"),
                    sample("d", "61"),
                    sample("e", "62")),
                null);

    assertThat(result.percent()).isEqualByComparingTo("120.000000");
    assertThat(result.referenceSeconds()).isEqualByComparingTo("60.000000");
    assertThat(result.provenance()).isEqualTo(ValueProvenance.DERIVED);
    assertThat(result.contributingFeatureSetIds()).containsExactly("a", "b", "c", "d", "e");
  }

  @Test
  void marksExternallySuppliedIdealCycleAsAssumed() {
    var result =
        new OperationalEffectivenessPolicy()
            .performance("target", "P1", seconds("50"), List.of(), new BigDecimal("45"));

    assertThat(result.percent()).isEqualByComparingTo("90.000000");
    assertThat(result.provenance()).isEqualTo(ValueProvenance.ASSUMED);
    assertThat(result.referenceKind()).isEqualTo("ASSUMED_IDEAL_CYCLE");
  }

  @Test
  void ignoresCounterResetAndDoesNotBridgeUnavailableGap() {
    var result =
        new OperationalEffectivenessPolicy()
            .throughput(
                List.of(
                    part(0, "10"),
                    part(1, "13"),
                    unavailable(2),
                    part(3, "20"),
                    part(4, "2"),
                    part(5, "4")));

    assertThat(result.partCount()).isEqualByComparingTo("5");
    assertThat(result.usedTransitionCount()).isEqualTo(2);
    assertThat(result.resetCount()).isEqualTo(1);
    assertThat(result.unavailableObservationCount()).isEqualTo(1);
  }

  @Test
  void neverCreatesCompositeOeeWithoutQualityInput() {
    var policy = new OperationalEffectivenessPolicy();

    assertThat(policy.unavailableQuality().status()).isEqualTo(ComponentStatus.UNAVAILABLE);
    assertThat(policy.unavailableComposite().percent()).isNull();
    assertThat(policy.unavailableComposite().reason()).isEqualTo("QUALITY_COMPONENT_UNAVAILABLE");
  }

  private static CyclePerformanceSample sample(String id, String seconds) {
    return new CyclePerformanceSample(id, "P1", START.minusSeconds(60), new BigDecimal(seconds));
  }

  private static BigDecimal seconds(String value) {
    return new BigDecimal(value);
  }

  private static PartCountObservation part(long sequence, String value) {
    return new PartCountObservation(
        sequence,
        START.plusSeconds(sequence * 60),
        "event-" + sequence,
        true,
        new BigDecimal(value));
  }

  private static PartCountObservation unavailable(long sequence) {
    return new PartCountObservation(
        sequence, START.plusSeconds(sequence * 60), "event-" + sequence, false, null);
  }
}
