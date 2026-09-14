package com.forgesync.factoryapi.production.adapter.inbound.rest;

import com.forgesync.factoryapi.production.domain.ObservedProductionContext;

final class ObservedProductionContextResponseMapper {
  ObservedProductionContextResponse map(ObservedProductionContext context) {
    return new ObservedProductionContextResponse(
        "1.0.0",
        context.ruleVersion(),
        context.machineId(),
        context.replaySessionId(),
        context.throughReplaySequence(),
        context.machiningRunProcessingRunId(),
        context.programIntervals().stream()
            .map(
                interval ->
                    new ObservedProductionContextResponse.ProgramInterval(
                        interval.kind(),
                        interval.availability(),
                        interval.programName(),
                        interval.startedAt(),
                        interval.endedAt(),
                        evidence(interval.startEvidence()),
                        evidence(interval.endEvidence())))
            .toList(),
        context.programSummaries().stream()
            .map(
                summary ->
                    new ObservedProductionContextResponse.ProgramSummary(
                        summary.programName(),
                        summary.runCount(),
                        summary.completedRunCount(),
                        summary.totalDurationSeconds(),
                        summary.meanDurationSeconds(),
                        summary.medianDurationSeconds(),
                        summary.machiningRunIds()))
            .toList(),
        context.unassignedRunCount(),
        new ObservedProductionContextResponse.PartCount(
            context.partCount().status(),
            context.partCount().netIncrease(),
            context.partCount().usedTransitionCount(),
            context.partCount().resetCount(),
            context.partCount().unavailableObservationCount(),
            context.partCount().reason(),
            context.partCount().associations().stream()
                .map(
                    association ->
                        new ObservedProductionContextResponse.Association(
                            association.observedIncrease(),
                            evidence(association.fromEvidence()),
                            evidence(association.toEvidence()),
                            association.relationship(),
                            association.overlappingMachiningRunIds()))
                .toList()),
        "NOT_OBSERVED");
  }

  private static ObservedProductionContextResponse.Evidence evidence(
      ObservedProductionContext.Evidence evidence) {
    if (evidence == null) return null;
    return new ObservedProductionContextResponse.Evidence(
        evidence.replaySequence(),
        evidence.sourceObservedAt(),
        evidence.sourceEventKey(),
        evidence.rawRecordId(),
        evidence.sourceDataItemId());
  }
}
