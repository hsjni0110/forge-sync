package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.CounterDelta;
import com.forgesync.factoryapi.equipmenttwin.domain.CounterRatio;
import com.forgesync.factoryapi.equipmenttwin.domain.CounterUtilization;
import com.forgesync.factoryapi.equipmenttwin.domain.StateUtilization;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationComparison;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiReport;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationState;
import java.util.Arrays;

final class UtilizationKpiResponseMapper {

  private static final String STATE_FORMULA = "STATE_DURATION / OBSERVED_RANGE";
  private static final String AUTOMATIC_FORMULA = "AUTO_DELTA / TOTAL_DELTA";
  private static final String CUTTING_FORMULA = "CUT_DELTA / AUTO_DELTA";

  UtilizationKpiResponse map(UtilizationKpiProcessingResult processing) {
    UtilizationKpiReport report = processing.report();
    return new UtilizationKpiResponse(
        "1.0.0",
        processing.processingRunId(),
        report.machineId(),
        report.replaySessionId().toString(),
        report.throughReplaySequence(),
        report.calculationVersion(),
        report.intervalProcessingRunId(),
        report.observedFrom().toString(),
        report.observedTo().toString(),
        report.inputHash(),
        report.resultHash(),
        processing.createdAt().toString(),
        mapState(report.stateUtilization()),
        mapCounters(report.counterUtilization()),
        mapComparison(report.comparison()));
  }

  private static UtilizationKpiResponse.State mapState(StateUtilization state) {
    return new UtilizationKpiResponse.State(
        state.status().name(),
        state.reason() == null ? null : state.reason().name(),
        "OBSERVED_EXECUTION_INTERVALS",
        "DERIVED",
        state.denominatorDuration().toString(),
        state.uncoveredDuration().toString(),
        STATE_FORMULA,
        Arrays.stream(UtilizationState.values())
            .map(
                value ->
                    new UtilizationKpiResponse.StateDuration(
                        value.name(),
                        state.durationOf(value).toString(),
                        state.ratioPercentOf(value)))
            .toList());
  }

  private static UtilizationKpiResponse.Counters mapCounters(CounterUtilization counters) {
    return new UtilizationKpiResponse.Counters(
        "OBSERVED_ACCUMULATED_TIME",
        "DERIVED",
        java.util.List.of(
            mapDelta(counters.totalDelta()),
            mapDelta(counters.automaticDelta()),
            mapDelta(counters.cuttingDelta())),
        mapRatio(counters.automaticRatio(), AUTOMATIC_FORMULA),
        mapRatio(counters.cuttingRatio(), CUTTING_FORMULA));
  }

  private static UtilizationKpiResponse.CounterDelta mapDelta(CounterDelta delta) {
    return new UtilizationKpiResponse.CounterDelta(
        delta.metric().name(),
        delta.valueSeconds(),
        "SECOND",
        "DERIVED",
        delta.usedTransitionCount(),
        delta.resetCount(),
        delta.unavailableObservationCount());
  }

  private static UtilizationKpiResponse.Ratio mapRatio(CounterRatio ratio, String formula) {
    return new UtilizationKpiResponse.Ratio(
        ratio.status().name(),
        ratio.ratioPercent(),
        ratio.reason() == null ? null : ratio.reason().name(),
        formula);
  }

  private static UtilizationKpiResponse.Comparison mapComparison(UtilizationComparison comparison) {
    return new UtilizationKpiResponse.Comparison(
        comparison.status().name(),
        comparison.intervalActivePercent(),
        comparison.counterAutomaticPercent(),
        comparison.counterAutomaticMinusIntervalActivePercentagePoints(),
        comparison.reason() == null ? null : comparison.reason().name(),
        "DIFFERENT_EVIDENCE_PATHS_NOT_EQUIVALENT");
  }
}
