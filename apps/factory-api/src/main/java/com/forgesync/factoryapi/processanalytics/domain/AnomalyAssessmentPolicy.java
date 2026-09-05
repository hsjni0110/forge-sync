package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class AnomalyAssessmentPolicy {
  public static final String POLICY_VERSION = "1.0.0";

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
    BigDecimal distance;
    BigDecimal score;
    String reasonCode = "BASELINE_IQR_DISTANCE";
    if (baseline.interquartileRange().compareTo(BigDecimal.ZERO) == 0) {
      boolean same = difference.compareTo(BigDecimal.ZERO) == 0;
      distance = same ? BigDecimal.ZERO.setScale(6) : null;
      score = (same ? BigDecimal.ZERO : BigDecimal.ONE).setScale(6);
      if (!same) reasonCode = "ZERO_IQR_DEVIATION";
    } else {
      BigDecimal rawDistance =
          difference.abs().divide(baseline.interquartileRange(), 12, RoundingMode.HALF_UP);
      distance = normalized(rawDistance);
      score =
          normalized(rawDistance.divide(BigDecimal.ONE.add(rawDistance), 12, RoundingMode.HALF_UP));
    }
    return new FeatureContribution(
        baseline.featureKey(),
        baseline.targetValue(),
        baseline.median(),
        difference,
        percentage,
        difference.signum() > 0 ? "ABOVE" : difference.signum() < 0 ? "BELOW" : "SAME",
        distance,
        score,
        baseline.sampleCount(),
        baseline.contributingFeatureSetIds(),
        reasonCode);
  }

  private static AnomalyClassification classify(BigDecimal score) {
    if (score.compareTo(new BigDecimal("0.500000")) < 0) return AnomalyClassification.NORMAL;
    if (score.compareTo(new BigDecimal("0.750000")) < 0) return AnomalyClassification.DEVIATING;
    return AnomalyClassification.HIGH_DEVIATION;
  }

  private static BigDecimal normalized(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP);
  }
}
