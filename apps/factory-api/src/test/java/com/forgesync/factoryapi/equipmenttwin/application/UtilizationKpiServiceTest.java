package com.forgesync.factoryapi.equipmenttwin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeMetric;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UtilizationKpiServiceTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final String INTERVAL_RUN = "sha256:" + "a".repeat(64);
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void reusesTheImmutableResultForTheSameIntervalVersionAndCounterInput() {
    FakeUtilizationKpiStore store = new FakeUtilizationKpiStore();
    FindEquipmentStateIntervals intervals =
        (machineId, processingRunId) ->
            new EquipmentStateIntervalProcessingResult(
                INTERVAL_RUN, START, false, intervalReport());
    UtilizationKpiService service =
        new UtilizationKpiService(
            intervals,
            (machineId, replaySessionId, throughReplaySequence) -> counters(),
            store,
            new UtilizationKpiPolicy(),
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
    UtilizationKpiCommand command =
        new UtilizationKpiCommand("Mazak01", INTERVAL_RUN, UtilizationKpiPolicy.RULE_VERSION);

    UtilizationKpiProcessingResult first = service.project(command);
    UtilizationKpiProcessingResult second = service.project(command);

    assertThat(first.isCreated()).isTrue();
    assertThat(second.isCreated()).isFalse();
    assertThat(first.processingRunId()).isEqualTo(second.processingRunId());
    assertThat(first.report().counterUtilization().automaticRatio().ratioPercent().toString())
        .isEqualTo("50.000000");
    assertThat(store.stored).hasSize(1);
  }

  private static EquipmentStateIntervalReport intervalReport() {
    List<StateSignalObservation> observations =
        List.of(execution(0, 0, "READY"), execution(1, 300, "ACTIVE"), mode(2, 600));
    EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();
    return EquipmentStateIntervalReport.of(
        EquipmentStateIntervalPolicy.RULE_VERSION,
        observations,
        policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
  }

  private static List<AccumulatedTimeObservation> counters() {
    return List.of(
        counterAt(AccumulatedTimeMetric.TOTAL, 0, -100, "0"),
        counterAt(AccumulatedTimeMetric.AUTO, 0, -100, "0"),
        counterAt(AccumulatedTimeMetric.CUT, 0, -100, "0"),
        counter(AccumulatedTimeMetric.TOTAL, 1, "100"),
        counter(AccumulatedTimeMetric.AUTO, 1, "20"),
        counter(AccumulatedTimeMetric.CUT, 1, "5"),
        counter(AccumulatedTimeMetric.TOTAL, 2, "300"),
        counter(AccumulatedTimeMetric.AUTO, 2, "120"),
        counter(AccumulatedTimeMetric.CUT, 2, "45"));
  }

  private static AccumulatedTimeObservation counter(
      AccumulatedTimeMetric metric, long sequence, String value) {
    return counterAt(metric, sequence, sequence == 1 ? 0 : 600, value);
  }

  private static AccumulatedTimeObservation counterAt(
      AccumulatedTimeMetric metric, long sequence, long seconds, String value) {
    return new AccumulatedTimeObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "counter-" + metric + "-" + sequence,
        metric,
        true,
        new BigDecimal(value));
  }

  private static StateSignalObservation execution(long sequence, long seconds, String value) {
    return new StateSignalObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "event-" + sequence,
        StateSignal.EXECUTION,
        true,
        value);
  }

  private static StateSignalObservation mode(long sequence, long seconds) {
    return new StateSignalObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "event-" + sequence,
        StateSignal.CONTROLLER_MODE,
        true,
        "AUTOMATIC");
  }

  private static final class FakeUtilizationKpiStore implements UtilizationKpiStore {
    private final Map<String, StoredUtilizationKpi> stored = new HashMap<>();

    @Override
    public Optional<StoredUtilizationKpi> findProcessingRun(String processingRunId) {
      return Optional.ofNullable(stored.get(processingRunId));
    }

    @Override
    public boolean preserve(StoredUtilizationKpi processing) {
      return stored.putIfAbsent(processing.processingRunId(), processing) == null;
    }
  }
}
