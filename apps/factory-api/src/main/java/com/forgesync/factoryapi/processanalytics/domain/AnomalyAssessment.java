package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AnomalyAssessment(
    String assessmentId,
    String machiningRunId,
    String targetFeatureSetId,
    AssessmentDataStatus dataStatus,
    AnomalyClassification classification,
    BigDecimal score,
    String primaryFeatureKey,
    int supportingOutlierCount,
    CycleBaseline baseline,
    Instant evaluatedStartedAt,
    ObservationRange evaluationSourceRange,
    List<ObservationProvenance> sourceProvenance,
    List<FeatureContribution> contributions,
    List<FeatureContribution> topReasons,
    String resultHash) {
  public AnomalyAssessment {
    sourceProvenance = List.copyOf(sourceProvenance);
    contributions = List.copyOf(contributions);
    topReasons = List.copyOf(topReasons);
  }
}
