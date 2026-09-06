package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentProcessingResult;
import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessment;
import com.forgesync.factoryapi.processanalytics.domain.FeatureBaseline;
import com.forgesync.factoryapi.processanalytics.domain.FeatureContribution;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;

final class AnomalyAssessmentResponseMapper {
  AnomalyAssessmentResponse map(AnomalyAssessmentProcessingResult result) {
    return new AnomalyAssessmentResponse(
        "1.2.0",
        result.assessmentProcessingRunId(),
        result.cycleFeatureProcessingRunId(),
        result.machiningRunProcessingRunId(),
        result.machineId(),
        result.cycleFeatureVersion(),
        result.baselinePolicyVersion(),
        result.anomalyAssessmentVersion(),
        result.inputHash(),
        result.resultHash(),
        result.createdAt(),
        result.assessments().stream().map(value -> mapAssessment(result, value)).toList());
  }

  private AnomalyAssessmentResponse.AssessmentDto mapAssessment(
      AnomalyAssessmentProcessingResult result, AnomalyAssessment assessment) {
    var baseline = assessment.baseline();
    return new AnomalyAssessmentResponse.AssessmentDto(
        assessment.assessmentId(),
        assessment.machiningRunId(),
        assessment.targetFeatureSetId(),
        assessment.dataStatus().name(),
        assessment.classification() == null ? null : assessment.classification().name(),
        assessment.score(),
        assessment.primaryFeatureKey(),
        assessment.supportingOutlierCount(),
        new AnomalyAssessmentResponse.BaselineDto(
            baseline.baselineGroupId(),
            baseline.machineId(),
            baseline.programName(),
            baseline.cycleFeatureVersion(),
            baseline.baselinePolicyVersion(),
            baseline.targetFeatureSetId(),
            baseline.candidateFeatureSetIds(),
            baseline.trainingStartedAt(),
            baseline.trainingEndedAt(),
            baseline.trainingSourceRanges().stream().map(this::mapRange).toList(),
            baseline.featureBaselines().stream().map(this::mapBaseline).toList()),
        assessment.evaluationSourceRange() == null
            ? null
            : mapRange(assessment.evaluationSourceRange()),
        assessment.contributions().stream().map(this::mapContribution).toList(),
        assessment.topReasons().stream().map(this::mapContribution).toList(),
        new AnomalyAssessmentResponse.LineageDto(
            "DERIVED",
            new AnomalyAssessmentResponse.DerivedCycleFeatureDto(
                "DERIVED", result.cycleFeatureProcessingRunId(), result.cycleFeatureVersion()),
            assessment.sourceProvenance().stream().map(this::mapSource).distinct().toList()),
        assessment.resultHash());
  }

  private AnomalyAssessmentResponse.FeatureBaselineDto mapBaseline(FeatureBaseline baseline) {
    return new AnomalyAssessmentResponse.FeatureBaselineDto(
        baseline.featureKey(),
        baseline.targetValue(),
        baseline.median(),
        baseline.firstQuartile(),
        baseline.thirdQuartile(),
        baseline.interquartileRange(),
        baseline.sampleCount(),
        baseline.contributingFeatureSetIds(),
        baseline.unavailableReason(),
        baseline.isAvailable());
  }

  private AnomalyAssessmentResponse.ContributionDto mapContribution(
      FeatureContribution contribution) {
    return new AnomalyAssessmentResponse.ContributionDto(
        contribution.featureKey(),
        contribution.targetValue(),
        contribution.baselineMedian(),
        contribution.difference(),
        contribution.percentageDifference(),
        contribution.direction(),
        contribution.distance(),
        contribution.deviationScale(),
        contribution.score(),
        contribution.sampleCount(),
        contribution.contributingFeatureSetIds(),
        contribution.reasonCode());
  }

  private AnomalyAssessmentResponse.ObservationRangeDto mapRange(ObservationRange range) {
    return new AnomalyAssessmentResponse.ObservationRangeDto(
        range.replaySessionId(),
        range.firstReplaySequence(),
        range.lastReplaySequence(),
        range.firstSourceObservedAt(),
        range.lastSourceObservedAt(),
        range.firstSourceEventKey(),
        range.lastSourceEventKey());
  }

  private AnomalyAssessmentResponse.SourceLineageDto mapSource(ObservationProvenance source) {
    return new AnomalyAssessmentResponse.SourceLineageDto(
        source.sourceKind(),
        source.provider(),
        source.sourceSetId(),
        source.artifactId(),
        source.rawRecordId(),
        source.mappingVersion(),
        source.sourceDataItemId());
  }
}
