package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunStatus;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;
import com.forgesync.factoryapi.processanalytics.domain.ProcessSignal;
import com.forgesync.factoryapi.processanalytics.domain.SegmentationConfidence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record MachiningRunDocument(
    String schemaVersion,
    String machiningRunId,
    String processingRunId,
    String segmentationRuleVersion,
    String machineId,
    String status,
    String programName,
    Instant startedAt,
    Instant endedAt,
    ConfidenceDocument confidence,
    ObservationRangeDocument sourceObservationRange,
    EvidenceDocument startEvidence,
    EvidenceDocument endEvidence,
    List<EvidenceDocument> supportingEvidence,
    ProvenanceDocument provenance,
    String resultHash) {

  static MachiningRunDocument from(MachiningRun run) {
    ObservationProvenance source = run.startEvidence().provenance();
    return new MachiningRunDocument(
        "1.0.0",
        run.machiningRunId(),
        run.processingRunId(),
        run.segmentationRuleVersion(),
        run.machineId(),
        run.status().name(),
        run.programName(),
        run.startedAt(),
        run.endedAt(),
        new ConfidenceDocument(run.confidence().name(), run.confidenceReasons()),
        ObservationRangeDocument.from(run.observationRange()),
        EvidenceDocument.from(run.startEvidence()),
        run.endEvidence() == null ? null : EvidenceDocument.from(run.endEvidence()),
        run.supportingEvidence().stream().map(EvidenceDocument::from).toList(),
        new ProvenanceDocument(
            "DERIVED",
            new SourceDocument(
                source.sourceKind(), source.provider(), source.sourceSetId(), source.artifactId()),
            new ProcessingDocument(run.processingRunId(), run.segmentationRuleVersion())),
        run.resultHash());
  }

  MachiningRun toDomain() {
    return new MachiningRun(
        machiningRunId,
        processingRunId,
        segmentationRuleVersion,
        machineId,
        MachiningRunStatus.valueOf(status),
        programName,
        startedAt,
        endedAt,
        SegmentationConfidence.valueOf(confidence.level()),
        confidence.reasons(),
        sourceObservationRange.toDomain(),
        startEvidence.toDomain(),
        endEvidence == null ? null : endEvidence.toDomain(),
        supportingEvidence.stream().map(EvidenceDocument::toDomain).toList(),
        resultHash);
  }

  record ConfidenceDocument(String level, List<String> reasons) {}

  record ObservationRangeDocument(
      UUID replaySessionId,
      long firstReplaySequence,
      long lastReplaySequence,
      Instant firstSourceObservedAt,
      Instant lastSourceObservedAt,
      String firstSourceEventKey,
      String lastSourceEventKey) {
    static ObservationRangeDocument from(ObservationRange range) {
      return new ObservationRangeDocument(
          range.replaySessionId(),
          range.firstReplaySequence(),
          range.lastReplaySequence(),
          range.firstSourceObservedAt(),
          range.lastSourceObservedAt(),
          range.firstSourceEventKey(),
          range.lastSourceEventKey());
    }

    ObservationRange toDomain() {
      return new ObservationRange(
          replaySessionId,
          firstReplaySequence,
          lastReplaySequence,
          firstSourceObservedAt,
          lastSourceObservedAt,
          firstSourceEventKey,
          lastSourceEventKey);
    }
  }

  record EvidenceDocument(
      String role,
      String signal,
      String sourceEventKey,
      long replaySequence,
      Instant sourceObservedAt,
      SourceDocument source,
      TransformationDocument transformation) {
    static EvidenceDocument from(BoundaryEvidence evidence) {
      ObservationProvenance provenance = evidence.provenance();
      return new EvidenceDocument(
          evidence.role(),
          evidence.signal().name(),
          evidence.sourceEventKey(),
          evidence.replaySequence(),
          evidence.sourceObservedAt(),
          new SourceDocument(
              provenance.sourceKind(),
              provenance.provider(),
              provenance.sourceSetId(),
              provenance.artifactId()),
          new TransformationDocument(
              provenance.rawRecordId(),
              provenance.mappingVersion(),
              provenance.sourceDataItemId()));
    }

    BoundaryEvidence toDomain() {
      return new BoundaryEvidence(
          role,
          ProcessSignal.valueOf(signal),
          sourceEventKey,
          replaySequence,
          sourceObservedAt,
          new ObservationProvenance(
              source.kind(),
              source.provider(),
              source.sourceSetId(),
              source.artifactId(),
              transformation.rawRecordId(),
              transformation.mappingVersion(),
              transformation.sourceDataItemId()));
    }
  }

  record ProvenanceDocument(
      String origin, SourceDocument source, ProcessingDocument transformation) {}

  record SourceDocument(String kind, String provider, String sourceSetId, String artifactId) {}

  record TransformationDocument(
      String rawRecordId, String mappingVersion, String sourceDataItemId) {}

  record ProcessingDocument(String processingRunId, String segmentationRuleVersion) {}
}
