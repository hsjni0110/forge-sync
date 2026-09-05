package com.forgesync.factoryapi.processanalytics.application;

public record ProcessAnomalyAssessmentsCommand(
    String machineId,
    String cycleFeatureProcessingRunId,
    String baselinePolicyVersion,
    String anomalyAssessmentVersion) {}
