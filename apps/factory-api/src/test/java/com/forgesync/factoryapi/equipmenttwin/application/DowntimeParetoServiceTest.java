package com.forgesync.factoryapi.equipmenttwin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.equipmenttwin.domain.ConditionEvidenceObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateInterval;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.IntervalBoundaryEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DowntimeParetoServiceTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final String INTERVAL_RUN = "sha256:" + "a".repeat(64);
  private static final String UTILIZATION_RUN = "sha256:" + "b".repeat(64);
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void reusesTheImmutableResultForTheSameUtilizationAndConditionEvidence() {
    EquipmentStateIntervalReport intervalReport = intervalReport();
    var utilizationReport =
        new UtilizationKpiPolicy().calculate(INTERVAL_RUN, intervalReport, List.of());
    FindUtilizationKpis utilizationFinder =
        (machineId, processingRunId) ->
            new UtilizationKpiProcessingResult(UTILIZATION_RUN, START, false, utilizationReport);
    FindEquipmentStateIntervals intervalFinder =
        (machineId, processingRunId) ->
            new EquipmentStateIntervalProcessingResult(INTERVAL_RUN, START, false, intervalReport);
    FakeStore store = new FakeStore();
    DowntimeParetoService service =
        new DowntimeParetoService(
            utilizationFinder,
            intervalFinder,
            (machineId, replaySessionId, throughReplaySequence) -> List.of(condition()),
            store,
            new DowntimeParetoPolicy(),
            Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));
    DowntimeParetoCommand command =
        new DowntimeParetoCommand("Mazak01", UTILIZATION_RUN, DowntimeParetoPolicy.RULE_VERSION);

    DowntimeParetoProcessingResult first = service.project(command);
    DowntimeParetoProcessingResult second = service.project(command);

    assertThat(first.isCreated()).isTrue();
    assertThat(second.isCreated()).isFalse();
    assertThat(first.processingRunId()).isEqualTo(second.processingRunId());
    assertThat(first.report().entries()).hasSize(1);
    assertThat(first.report().intervalProcessingRunId()).isEqualTo(INTERVAL_RUN);
    assertThat(store.stored).hasSize(1);
  }

  private static EquipmentStateIntervalReport intervalReport() {
    EquipmentStateInterval stopped =
        new EquipmentStateInterval(
            StateSignal.EXECUTION,
            "STOPPED",
            START,
            START.plusSeconds(60),
            new IntervalBoundaryEvidence(1, START, "start"),
            new IntervalBoundaryEvidence(2, START.plusSeconds(60), "end"));
    return new EquipmentStateIntervalReport(
        "1.0.0",
        "Mazak01",
        SESSION,
        2,
        START,
        START.plusSeconds(60),
        2,
        "sha256:" + "c".repeat(64),
        "sha256:" + "d".repeat(64),
        List.of(stopped),
        List.of());
  }

  private static ConditionEvidenceObservation condition() {
    return new ConditionEvidenceObservation(
        "Mazak01",
        SESSION,
        1,
        START.plusSeconds(30),
        "condition",
        "Mazak01-controller",
        "SYSTEM",
        "WARNING",
        "406",
        "MEMORY PROTECT");
  }

  private static final class FakeStore implements DowntimeParetoStore {
    private final Map<String, StoredDowntimePareto> stored = new HashMap<>();

    @Override
    public Optional<StoredDowntimePareto> findProcessingRun(String processingRunId) {
      return Optional.ofNullable(stored.get(processingRunId));
    }

    @Override
    public boolean preserve(StoredDowntimePareto processing) {
      return stored.putIfAbsent(processing.processingRunId(), processing) == null;
    }
  }
}
