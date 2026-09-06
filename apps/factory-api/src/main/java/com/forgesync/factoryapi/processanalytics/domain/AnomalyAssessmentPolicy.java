package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Scores a cycle feature set against the baseline of earlier runs in the same group.
 *
 * <p>The deviation scale is the interquartile range of the baseline samples, floored so that a
 * group whose samples are nearly identical cannot turn ordinary variation into an extreme distance.
 * On the NIST Mazak01 observation set the machining cycle is consistent to about a second, which
 * left an interquartile range of one to two seconds; a twelve second difference then measured six
 * to twelve interquartile ranges and 98% of the evaluable runs were reported as deviating. The
 * floors below express what counts as ordinary variation before the sample spread is allowed to
 * speak.
 */
public final class AnomalyAssessmentPolicy {
  public static final String POLICY_VERSION = "2.0.0";

  /**
   * Fraction of the baseline median treated as ordinary variation. Cutting cycles move by a few
   * percent from tool wear, material variation and operator overrides without the process having
   * changed, so a difference within a tenth of the median is not evidence of a deviation.
   */
  static final BigDecimal RELATIVE_SCALE_FLOOR_RATIO = new BigDecimal("0.10");

  /**
   * Resolution floor for the duration features, whose unit is known to be seconds. Cycle boundaries
   * come from discrete execution events, so sub-second differences carry no meaning.
   */
  static final BigDecimal SECONDS_SCALE_FLOOR = BigDecimal.ONE;

  static final Set<String> SECONDS_FEATURE_KEYS =
      Set.of("durationSeconds", "cuttingSeconds", "idleSeconds");

  /** Distance, in effective scales, at which a difference is first reported as a deviation. */
  static final BigDecimal DEVIATING_DISTANCE = BigDecimal.ONE;

  /** Distance, in effective scales, at which a deviation is reported as a large one. */
  static final BigDecimal HIGH_DEVIATION_DISTANCE = new BigDecimal("3");

  public AnomalyAssessment assess(
      String assessmentId, CycleFeatureContext target, CycleBaseline baseline) {
    List<FeatureContribution> contributions =
        baseline.featureBaselines().stream()
            .filter(FeatureBaseline::isAvailable)
            .map(AnomalyAssessmentPolicy::contribution)
            .sorted(
                Comparator.comparing(FeatureContribution::score)
                    .reversed()
                    .thenComparing(FeatureContribution::featureKey))
            .toList();
    boolean hasComparableTarget =
        baseline.featureBaselines().stream()
            .anyMatch(
                value ->
                    value.unavailableReason() == null
                        || !value.unavailableReason().startsWith("TARGET_"));
    int unavailableCount =
        (int) baseline.featureBaselines().stream().filter(value -> !value.isAvailable()).count();
    AssessmentDataStatus dataStatus =
        status(target, hasComparableTarget, contributions, unavailableCount);
    BigDecimal score = contributions.isEmpty() ? null : contributions.getFirst().score();
    AnomalyClassification classification = score == null ? null : classify(score);
    List<FeatureContribution> topReasons = contributions.stream().limit(3).toList();
    String resultHash =
        DeterministicHash.sha256(
            CycleBaselinePolicy.lengthPrefixed(
                assessmentId,
                target.cycleFeatureSetId(),
                dataStatus,
                classification,
                score,
                contributions));
    return new AnomalyAssessment(
        assessmentId,
        target.cycleFeature().machiningRunId(),
        target.cycleFeatureSetId(),
        dataStatus,
        classification,
        score,
        baseline,
        target.cycleFeature().startedAt(),
        target.cycleFeature().sourceObservationRange(),
        target.cycleFeature().contributingProvenance(),
        contributions,
        topReasons,
        resultHash);
  }

  private static AssessmentDataStatus status(
      CycleFeatureContext target,
      boolean hasTarget,
      List<FeatureContribution> contributions,
      int unavailableCount) {
    if (target.programName() == null || target.programName().isBlank() || !hasTarget) {
      return AssessmentDataStatus.UNAVAILABLE;
    }
    if (contributions.isEmpty()) return AssessmentDataStatus.INSUFFICIENT_DATA;
    if (unavailableCount > 0) return AssessmentDataStatus.PARTIAL;
    return AssessmentDataStatus.AVAILABLE;
  }

  private static FeatureContribution contribution(FeatureBaseline baseline) {
    BigDecimal difference = normalized(baseline.targetValue().subtract(baseline.median()));
    BigDecimal percentage =
        baseline.median().compareTo(BigDecimal.ZERO) == 0
            ? null
            : normalized(
                difference
                    .divide(baseline.median().abs(), 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
    DeviationScale scale = deviationScale(baseline);
    BigDecimal distance;
    BigDecimal score;
    String reasonCode;
    if (scale.value().signum() == 0) {
      // Median and spread are both zero and the unit is unknown, so no scale can be established.
      boolean same = difference.compareTo(BigDecimal.ZERO) == 0;
      distance = same ? BigDecimal.ZERO.setScale(6) : null;
      score = (same ? BigDecimal.ZERO : BigDecimal.ONE).setScale(6);
      reasonCode = same ? scale.reasonCode() : "ZERO_SCALE_DEVIATION";
    } else {
      BigDecimal rawDistance = difference.abs().divide(scale.value(), 12, RoundingMode.HALF_UP);
      distance = normalized(rawDistance);
      score =
          normalized(rawDistance.divide(BigDecimal.ONE.add(rawDistance), 12, RoundingMode.HALF_UP));
      reasonCode = scale.reasonCode();
    }
    return new FeatureContribution(
        baseline.featureKey(),
        baseline.targetValue(),
        baseline.median(),
        difference,
        percentage,
        difference.signum() > 0 ? "ABOVE" : difference.signum() < 0 ? "BELOW" : "SAME",
        distance,
        normalized(scale.value()),
        score,
        baseline.sampleCount(),
        baseline.contributingFeatureSetIds(),
        reasonCode);
  }

  /**
   * The larger of the sample spread and the floors. Reporting which one bound the scale lets a
   * reader see whether the comparison rests on observed spread or on the ordinary-variation floor.
   */
  private static DeviationScale deviationScale(FeatureBaseline baseline) {
    BigDecimal interquartileRange =
        baseline.interquartileRange() == null ? BigDecimal.ZERO : baseline.interquartileRange();
    BigDecimal relativeFloor = baseline.median().abs().multiply(RELATIVE_SCALE_FLOOR_RATIO);
    BigDecimal absoluteFloor =
        SECONDS_FEATURE_KEYS.contains(baseline.featureKey())
            ? SECONDS_SCALE_FLOOR
            : BigDecimal.ZERO;
    if (interquartileRange.compareTo(relativeFloor) >= 0
        && interquartileRange.compareTo(absoluteFloor) >= 0) {
      return new DeviationScale(interquartileRange, "BASELINE_IQR_DISTANCE");
    }
    if (relativeFloor.compareTo(absoluteFloor) >= 0) {
      return new DeviationScale(relativeFloor, "RELATIVE_SCALE_FLOOR");
    }
    return new DeviationScale(absoluteFloor, "ABSOLUTE_SCALE_FLOOR");
  }

  private static AnomalyClassification classify(BigDecimal score) {
    if (score.compareTo(saturated(DEVIATING_DISTANCE)) < 0) return AnomalyClassification.NORMAL;
    if (score.compareTo(saturated(HIGH_DEVIATION_DISTANCE)) < 0) {
      return AnomalyClassification.DEVIATING;
    }
    return AnomalyClassification.HIGH_DEVIATION;
  }

  /** Maps a distance in effective scales onto the same saturating curve the scores use. */
  private static BigDecimal saturated(BigDecimal distance) {
    return normalized(distance.divide(BigDecimal.ONE.add(distance), 12, RoundingMode.HALF_UP));
  }

  private record DeviationScale(BigDecimal value, String reasonCode) {}

  private static BigDecimal normalized(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP);
  }
}
