package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingResult;
import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;

final class MachiningRunResponseMapper {

  MachiningRunResponse map(MachiningRunProcessingResult result) {
    return new MachiningRunResponse(
        "1.0.0",
        result.processingRunId(),
        result.machineId(),
        result.replaySessionId(),
        result.throughReplaySequence(),
        result.segmentationRuleVersion(),
        result.inputHash(),
        result.inputObservationCount(),
        result.resultHash(),
        result.createdAt(),
        result.machiningRuns().stream().map(this::mapRun).toList());
  }

  private MachiningRunResponse.RunDto mapRun(MachiningRun run) {
    ObservationProvenance source = run.startEvidence().provenance();
    return new MachiningRunResponse.RunDto(
        run.machiningRunId(),
        run.processingRunId(),
        run.segmentationRuleVersion(),
        run.machineId(),
        run.status().name(),
        run.programName(),
        run.startedAt(),
        run.endedAt(),
        new MachiningRunResponse.ConfidenceDto(run.confidence().name(), run.confidenceReasons()),
        mapRange(run.observationRange()),
        mapEvidence(run.startEvidence()),
        run.endEvidence() == null ? null : mapEvidence(run.endEvidence()),
        run.supportingEvidence().stream().map(MachiningRunResponseMapper::mapEvidence).toList(),
        new MachiningRunResponse.ProvenanceDto(
            "DERIVED",
            mapSource(source),
            new MachiningRunResponse.ProcessingTransformationDto(
                run.processingRunId(), run.segmentationRuleVersion())),
        run.resultHash());
  }

  private static MachiningRunResponse.ObservationRangeDto mapRange(ObservationRange range) {
    return new MachiningRunResponse.ObservationRangeDto(
        range.replaySessionId(),
        range.firstReplaySequence(),
        range.lastReplaySequence(),
        range.firstSourceObservedAt(),
        range.lastSourceObservedAt(),
        range.firstSourceEventKey(),
        range.lastSourceEventKey());
  }

  private static MachiningRunResponse.EvidenceDto mapEvidence(BoundaryEvidence evidence) {
    ObservationProvenance provenance = evidence.provenance();
    return new MachiningRunResponse.EvidenceDto(
        evidence.role(),
        evidence.signal().name(),
        evidence.sourceEventKey(),
        evidence.replaySequence(),
        evidence.sourceObservedAt(),
        mapSource(provenance),
        new MachiningRunResponse.TransformationDto(
            provenance.rawRecordId(), provenance.mappingVersion(), provenance.sourceDataItemId()));
  }

  private static MachiningRunResponse.SourceDto mapSource(ObservationProvenance provenance) {
    return new MachiningRunResponse.SourceDto(
        provenance.sourceKind(),
        provenance.provider(),
        provenance.sourceSetId(),
        provenance.artifactId());
  }
}
