package com.forgesync.factoryapi.processanalytics.adapter;

import com.forgesync.factoryapi.equipmenttwin.application.FindUtilizationKpis;
import com.forgesync.factoryapi.equipmenttwin.domain.KpiDataStatus;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationState;
import com.forgesync.factoryapi.processanalytics.application.UtilizationEvidenceSource;
import java.util.Objects;

final class EquipmentTwinUtilizationEvidenceAdapter implements UtilizationEvidenceSource {
  private final FindUtilizationKpis source;

  EquipmentTwinUtilizationEvidenceAdapter(FindUtilizationKpis source) {
    this.source = Objects.requireNonNull(source);
  }

  @Override
  public UtilizationEvidence find(String machineId, String processingRunId) {
    var processing = source.findByProcessingRunId(machineId, processingRunId);
    var report = processing.report();
    var state = report.stateUtilization();
    boolean available = state.status() != KpiDataStatus.UNAVAILABLE;
    return new UtilizationEvidence(
        processing.processingRunId(),
        report.replaySessionId(),
        report.throughReplaySequence(),
        report.observedFrom(),
        report.observedTo(),
        report.resultHash(),
        available ? state.ratioPercentOf(UtilizationState.ACTIVE) : null,
        available);
  }
}
