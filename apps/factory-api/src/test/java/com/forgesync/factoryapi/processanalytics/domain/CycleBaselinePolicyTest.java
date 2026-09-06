package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CycleBaselinePolicyTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void calculatesExclusiveMedianQuartilesAndExplainableDeviation() {
    var target = context("target", 10, "16", "1.000000");
    var candidates =
        List.of(
            context("c8", 1, "8", "1.000000"),
            context("c9", 2, "9", "1.000000"),
            context("c10", 3, "10", "1.000000"),
            context("c11", 4, "11", "1.000000"),
            context("c12", 5, "12", "1.000000"));

    CycleBaseline baseline = new CycleBaselinePolicy().build("Mazak01", target, candidates);
    FeatureBaseline duration = baseline.featureBaselines().getFirst();
    AnomalyAssessment assessment =
        new AnomalyAssessmentPolicy().assess("assessment", target, baseline);

    assertThat(duration.featureKey()).isEqualTo("durationSeconds");
    assertThat(duration.median()).isEqualByComparingTo("10.000000");
    assertThat(duration.firstQuartile()).isEqualByComparingTo("8.500000");
    assertThat(duration.thirdQuartile()).isEqualByComparingTo("11.500000");
    assertThat(duration.interquartileRange()).isEqualByComparingTo("3.000000");
    assertThat(assessment.score()).isEqualByComparingTo("0.666667");
    assertThat(assessment.classification()).isEqualTo(AnomalyClassification.DEVIATING);
    assertThat(assessment.topReasons().getFirst().difference()).isEqualByComparingTo("6.000000");
    assertThat(assessment.topReasons().getFirst().percentageDifference())
        .isEqualByComparingTo("60.000000");
    var normalTarget = context("normal", 11, "10", "1.000000");
    var normal =
        new AnomalyAssessmentPolicy()
            .assess(
                "normal-assessment",
                normalTarget,
                new CycleBaselinePolicy().build("Mazak01", normalTarget, candidates));
    assertThat(normal.classification()).isEqualTo(AnomalyClassification.NORMAL);
  }

  @Test
  void calculatesEvenQuartilesAndKeepsSixDecimalHalfUpScale() {
    var target = context("target", 10, "10.5", "1.000000");
    var candidates = new ArrayList<CycleFeatureContext>();
    for (int value = 8; value <= 13; value++) {
      candidates.add(context("c" + value, value - 7, Integer.toString(value), "1.000000"));
    }

    FeatureBaseline baseline =
        new CycleBaselinePolicy()
            .build("Mazak01", target, candidates)
            .featureBaselines()
            .getFirst();

    assertThat(baseline.median()).isEqualByComparingTo("10.500000");
    assertThat(baseline.firstQuartile()).isEqualByComparingTo("9.000000");
    assertThat(baseline.thirdQuartile()).isEqualByComparingTo("12.000000");
  }

  @Test
  void appliesCoverageMinimumPerFeatureAndKeepsOnlyThirtyMostRecentSamples() {
    var candidates = new ArrayList<CycleFeatureContext>();
    for (int index = 1; index <= 31; index++) {
      String coverage = index == 31 ? "0.799999" : "0.800000";
      candidates.add(context("c" + index, index, Integer.toString(index), coverage));
    }
    var target = context("target", 40, "32", "0.800000");

    CycleBaseline baseline = new CycleBaselinePolicy().build("Mazak01", target, candidates);
    FeatureBaseline duration = find(baseline, "durationSeconds");
    FeatureBaseline cutting = find(baseline, "cuttingSeconds");

    assertThat(duration.sampleCount()).isEqualTo(30);
    assertThat(duration.contributingFeatureSetIds()).doesNotContain("c1");
    assertThat(duration.contributingFeatureSetIds()).contains("c31");
    assertThat(cutting.sampleCount()).isEqualTo(30);
    assertThat(cutting.contributingFeatureSetIds()).doesNotContain("c31");
  }

  @Test
  void reportsMinimumSampleAndZeroIqrWithoutGuessing() {
    var target = context("target", 10, "11", "1.000000");
    var four =
        List.of(
            context("c1", 1, "10", "1.000000"),
            context("c2", 2, "10", "1.000000"),
            context("c3", 3, "10", "1.000000"),
            context("c4", 4, "10", "1.000000"));
    CycleBaseline insufficient = new CycleBaselinePolicy().build("Mazak01", target, four);
    var insufficientAssessment = new AnomalyAssessmentPolicy().assess("a1", target, insufficient);

    assertThat(insufficientAssessment.dataStatus())
        .isEqualTo(AssessmentDataStatus.INSUFFICIENT_DATA);
    assertThat(insufficientAssessment.score()).isNull();

    var five = new ArrayList<>(four);
    five.add(context("c5", 5, "10", "1.000000"));
    var zeroIqr =
        new AnomalyAssessmentPolicy()
            .assess("a2", target, new CycleBaselinePolicy().build("Mazak01", target, five));

    // A baseline that never varies leaves no spread, so the ordinary-variation floor sets the
    // scale instead of turning one second into an extreme distance.
    assertThat(zeroIqr.topReasons().getFirst().deviationScale()).isEqualByComparingTo("1.000000");
    assertThat(zeroIqr.topReasons().getFirst().reasonCode()).isEqualTo("RELATIVE_SCALE_FLOOR");
    assertThat(zeroIqr.topReasons().getFirst().distance()).isEqualByComparingTo("1.000000");
    assertThat(zeroIqr.score()).isEqualByComparingTo("0.500000");
    assertThat(zeroIqr.classification()).isEqualTo(AnomalyClassification.DEVIATING);
  }

  @Test
  void treatsOrdinaryVariationOfAConsistentCycleAsWithinBaseline() {
    // The NIST Mazak01 cycle repeats at about two minutes with roughly a second of spread, which
    // left version 1.0.0 reporting ordinary variation as an extreme distance.
    var candidates =
        List.of(
            context("c1", 1, "120", "1.000000"),
            context("c2", 2, "121", "1.000000"),
            context("c3", 3, "121", "1.000000"),
            context("c4", 4, "122", "1.000000"),
            context("c5", 5, "121", "1.000000"));
    var ordinary = context("ordinary", 6, "133", "1.000000");
    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "ordinary",
                ordinary,
                new CycleBaselinePolicy().build("Mazak01", ordinary, candidates));

    assertThat(assessment.topReasons().getFirst().percentageDifference())
        .isEqualByComparingTo("9.917355");
    assertThat(assessment.topReasons().getFirst().reasonCode()).isEqualTo("RELATIVE_SCALE_FLOOR");
    assertThat(assessment.topReasons().getFirst().deviationScale())
        .isEqualByComparingTo("12.100000");
    assertThat(assessment.classification()).isEqualTo(AnomalyClassification.NORMAL);

    // A run that stopped after a few seconds is a different grade, not the same label.
    var aborted = context("aborted", 7, "12", "1.000000");
    var abortedAssessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "aborted",
                aborted,
                new CycleBaselinePolicy().build("Mazak01", aborted, candidates));

    assertThat(abortedAssessment.topReasons().getFirst().distance())
        .isEqualByComparingTo("9.008264");
    assertThat(abortedAssessment.classification()).isEqualTo(AnomalyClassification.HIGH_DEVIATION);
  }

  @Test
  void publishesTheAssessmentPolicyVersionThatProducedTheResult() {
    assertThat(AnomalyAssessmentPolicy.POLICY_VERSION).isEqualTo("3.0.0");
    assertThat(CycleBaselinePolicy.POLICY_VERSION).isEqualTo("1.0.0");
  }

  @Test
  void comparesOnlyAnExactlyMatchingMetricChannelAndReportsPartialCoverage() {
    var candidates = new ArrayList<CycleFeatureContext>();
    for (int index = 1; index <= 5; index++) {
      candidates.add(metricContext("c" + index, index, "rpm", "100", "1.000000"));
    }
    candidates.add(metricContext("other-unit", 6, "hertz", "999", "1.000000"));
    var target = metricContext("target", 10, "rpm", "110", "1.000000");

    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "assessment",
                target,
                new CycleBaselinePolicy().build("Mazak01", target, candidates));
    var metricMean =
        assessment.baseline().featureBaselines().stream()
            .filter(value -> value.featureKey().endsWith("|mean"))
            .findFirst()
            .orElseThrow();

    assertThat(metricMean.sampleCount()).isEqualTo(5);
    assertThat(metricMean.contributingFeatureSetIds()).doesNotContain("other-unit");
    assertThat(assessment.dataStatus()).isEqualTo(AssessmentDataStatus.PARTIAL);
    assertThat(assessment.baseline().featureBaselines())
        .anyMatch(
            value ->
                value.featureKey().equals("metric|LOAD|UNAVAILABLE_CHANNEL|mean")
                    && value.unavailableReason().equals("TARGET_VALUE_UNAVAILABLE"));
  }

  @Test
  void keepsPercentageNullForZeroMedianAndUsesExactClassificationBoundaries() {
    var target = context("target", 10, "1", "1.000000");
    var zeroBaseline =
        new FeatureBaseline(
            "durationSeconds",
            BigDecimal.ONE.setScale(6),
            BigDecimal.ZERO.setScale(6),
            BigDecimal.ZERO.setScale(6),
            BigDecimal.ZERO.setScale(6),
            BigDecimal.ZERO.setScale(6),
            5,
            List.of("1", "2", "3", "4", "5"),
            null);
    var cycleBaseline =
        new CycleBaseline(
            "group",
            "Mazak01",
            "PROGRAM-1",
            "1.0.0",
            "1.0.0",
            "target",
            List.of(),
            null,
            null,
            List.of(),
            List.of(zeroBaseline));

    var assessment = new AnomalyAssessmentPolicy().assess("assessment", target, cycleBaseline);

    // Median zero leaves no relative floor, so the seconds resolution floor sets the scale.
    assertThat(assessment.contributions().getFirst().percentageDifference()).isNull();
    assertThat(assessment.contributions().getFirst().reasonCode())
        .isEqualTo("ABSOLUTE_SCALE_FLOOR");
    assertThat(assessment.contributions().getFirst().deviationScale())
        .isEqualByComparingTo("1.000000");
    assertThat(assessment.classification()).isEqualTo(AnomalyClassification.DEVIATING);
  }

  @Test
  void reportsPartialWhenOnlySomeTargetFeaturesMeetTheSampleMinimum() {
    var candidates = new ArrayList<CycleFeatureContext>();
    for (int index = 1; index <= 5; index++) {
      candidates.add(
          metricContext("c" + index, index, "rpm", "100", index == 5 ? "0.799999" : "1.000000"));
    }
    var target = metricContext("target", 10, "rpm", "110", "1.000000");

    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "assessment",
                target,
                new CycleBaselinePolicy().build("Mazak01", target, candidates));

    assertThat(assessment.dataStatus()).isEqualTo(AssessmentDataStatus.PARTIAL);
    assertThat(assessment.contributions())
        .extracting(FeatureContribution::featureKey)
        .contains("durationSeconds")
        .doesNotContain("metric|SPINDLE_SPEED|spindle|speed|rpm|mean");
  }

  @Test
  void appliesClassificationBoundariesAndStableTopReasonTieBreak() {
    var target = context("target", 10, "12", "1.000000");
    var baselines = new ArrayList<FeatureBaseline>();
    for (String key : List.of("durationSeconds", "d", "c", "b", "a")) {
      baselines.add(
          new FeatureBaseline(
              key,
              new BigDecimal("12.000000"),
              new BigDecimal("10.000000"),
              new BigDecimal("9.000000"),
              new BigDecimal("11.000000"),
              new BigDecimal("2.000000"),
              5,
              List.of("1", "2", "3", "4", "5"),
              null));
    }
    var baseline =
        new CycleBaseline(
            "group",
            "Mazak01",
            "PROGRAM-1",
            "1.0.0",
            "1.0.0",
            "target",
            List.of(),
            null,
            null,
            List.of(),
            baselines);

    var deviating = new AnomalyAssessmentPolicy().assess("assessment", target, baseline);

    assertThat(deviating.score()).isEqualByComparingTo("0.500000");
    assertThat(deviating.classification()).isEqualTo(AnomalyClassification.DEVIATING);
    assertThat(deviating.primaryFeatureKey()).isEqualTo("durationSeconds");
    // The primary feature leads, then the remaining reasons keep their stable tie-break order.
    assertThat(deviating.topReasons())
        .extracting(FeatureContribution::featureKey)
        .containsExactly("durationSeconds", "a", "b");
    assertThat(deviating.supportingOutlierCount()).isEqualTo(4);
  }

  @Test
  void letsCycleDurationCarryTheGradeAndCountsChannelsBeside() {
    var target = context("target", 10, "121", "1.000000");
    var baselines =
        new ArrayList<>(
            List.of(
                // Cycle duration is ordinary: 121 against a median of 120.
                new FeatureBaseline(
                    "durationSeconds",
                    new BigDecimal("121.000000"),
                    new BigDecimal("120.000000"),
                    new BigDecimal("119.000000"),
                    new BigDecimal("121.000000"),
                    new BigDecimal("2.000000"),
                    5,
                    List.of("1", "2", "3", "4", "5"),
                    null)));
    // Two channels are far outside their own baselines.
    for (String key :
        List.of(
            "metric|PATH_FEEDRATE|p|p_7|MM/S|populationStandardDeviation",
            "metric|LOAD|Mazak01-B|Mazak01-B_1|PERCENT|mean")) {
      baselines.add(
          new FeatureBaseline(
              key,
              new BigDecimal("500.000000"),
              new BigDecimal("100.000000"),
              new BigDecimal("95.000000"),
              new BigDecimal("105.000000"),
              new BigDecimal("10.000000"),
              5,
              List.of("1", "2", "3", "4", "5"),
              null));
    }
    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "assessment",
                target,
                new CycleBaseline(
                    "group",
                    "Mazak01",
                    "PROGRAM-1",
                    "1.0.0",
                    "1.0.0",
                    "target",
                    List.of(),
                    null,
                    null,
                    List.of(),
                    baselines));

    assertThat(assessment.classification()).isEqualTo(AnomalyClassification.NORMAL);
    assertThat(assessment.primaryFeatureKey()).isEqualTo("durationSeconds");
    assertThat(assessment.supportingOutlierCount()).isEqualTo(2);
    assertThat(assessment.topReasons().getFirst().featureKey()).isEqualTo("durationSeconds");
  }

  @Test
  void withoutTheCycleDurationThereIsNothingToGrade() {
    var target = context("target", 10, "12", "1.000000");
    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "assessment",
                target,
                new CycleBaseline(
                    "group",
                    "Mazak01",
                    "PROGRAM-1",
                    "1.0.0",
                    "1.0.0",
                    "target",
                    List.of(),
                    null,
                    null,
                    List.of(),
                    List.of(
                        new FeatureBaseline(
                            "metric|LOAD|c|c_1|PERCENT|mean",
                            new BigDecimal("500.000000"),
                            new BigDecimal("100.000000"),
                            new BigDecimal("95.000000"),
                            new BigDecimal("105.000000"),
                            new BigDecimal("10.000000"),
                            5,
                            List.of("1", "2", "3", "4", "5"),
                            null))));

    assertThat(assessment.dataStatus()).isEqualTo(AssessmentDataStatus.INSUFFICIENT_DATA);
    assertThat(assessment.classification()).isNull();
    assertThat(assessment.primaryFeatureKey()).isNull();
    assertThat(assessment.supportingOutlierCount()).isEqualTo(1);
  }

  @Test
  void lengthPrefixesPreventDelimiterCollisions() {
    assertThat(CycleBaselinePolicy.lengthPrefixed("a|b", "c"))
        .isNotEqualTo(CycleBaselinePolicy.lengthPrefixed("a", "b|c"));
  }

  @Test
  void reportsUnavailableWhenTheTargetHasNoComparableP0Value() {
    var target =
        new CycleFeatureContext(
            "target",
            "PROGRAM-1",
            new CycleFeature(
                "1.0.0",
                "run-target",
                START,
                START,
                FeatureAvailability.EMPTY_WINDOW,
                null,
                null,
                null,
                new FeatureCoverage(BigDecimal.ZERO, BigDecimal.ZERO, null),
                List.of(),
                List.of(),
                null,
                List.of(),
                "result"));

    var assessment =
        new AnomalyAssessmentPolicy()
            .assess(
                "assessment",
                target,
                new CycleBaselinePolicy().build("Mazak01", target, List.of()));

    assertThat(assessment.dataStatus()).isEqualTo(AssessmentDataStatus.UNAVAILABLE);
    assertThat(assessment.score()).isNull();
    assertThat(assessment.baseline().featureBaselines())
        .allMatch(value -> value.unavailableReason().startsWith("TARGET_"));
  }

  private static FeatureBaseline find(CycleBaseline baseline, String key) {
    return baseline.featureBaselines().stream()
        .filter(feature -> feature.featureKey().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static CycleFeatureContext context(
      String id, long startOffset, String duration, String coverage) {
    Instant startedAt = START.plusSeconds(startOffset);
    BigDecimal durationValue = new BigDecimal(duration);
    var feature =
        new CycleFeature(
            "1.0.0",
            "run-" + id,
            startedAt,
            startedAt.plusSeconds(durationValue.longValue()),
            FeatureAvailability.AVAILABLE,
            durationValue,
            durationValue,
            BigDecimal.ZERO,
            new FeatureCoverage(durationValue, durationValue, new BigDecimal(coverage)),
            List.of(),
            List.of(),
            null,
            List.of(),
            "result-" + id);
    return new CycleFeatureContext(id, "PROGRAM-1", feature);
  }

  private static CycleFeatureContext metricContext(
      String id, long startOffset, String unit, String mean, String coverage) {
    CycleFeatureContext base = context(id, startOffset, "10", coverage);
    CycleFeature feature = base.cycleFeature();
    var metric =
        new MetricFeature(
            CycleMetric.SPINDLE_SPEED,
            "spindle",
            "speed",
            unit,
            FeatureAvailability.AVAILABLE,
            "TIME_WEIGHTED_LAST_OBSERVATION_CARRIED_FORWARD",
            new BigDecimal(mean),
            new BigDecimal(mean),
            BigDecimal.ZERO,
            new FeatureCoverage(BigDecimal.TEN, BigDecimal.TEN, new BigDecimal(coverage)),
            List.of(),
            List.of());
    return new CycleFeatureContext(
        id,
        "PROGRAM-1",
        new CycleFeature(
            feature.cycleFeatureVersion(),
            feature.machiningRunId(),
            feature.startedAt(),
            feature.endedAt(),
            feature.status(),
            feature.durationSeconds(),
            feature.cuttingSeconds(),
            feature.idleSeconds(),
            feature.stateCoverage(),
            feature.stateContributingProvenance(),
            List.of(metric),
            feature.sourceObservationRange(),
            feature.contributingProvenance(),
            feature.resultHash()));
  }
}
