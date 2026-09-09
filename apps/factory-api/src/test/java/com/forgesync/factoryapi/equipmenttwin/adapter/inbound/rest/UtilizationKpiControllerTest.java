package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.equipmenttwin.application.FindUtilizationKpis;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectUtilizationKpis;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeMetric;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class UtilizationKpiControllerTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final String INTERVAL_RUN = "sha256:" + "a".repeat(64);
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void createsAProjectionFromAnExplicitImmutableIntervalReference() {
    UtilizationKpiProcessingResult result = processingResult();
    ProjectUtilizationKpis project = command -> result;
    FindUtilizationKpis find = (machineId, processingRunId) -> result.asExisting();
    UtilizationKpiController controller = new UtilizationKpiController(project, find);
    UtilizationKpiController.ProcessingRequest request =
        new UtilizationKpiController.ProcessingRequest(
            INTERVAL_RUN, UtilizationKpiPolicy.RULE_VERSION);

    ResponseEntity<?> response = controller.project("Mazak01", request);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("application/vnd.forgesync.utilization-kpis.v1+json");
  }

  private static UtilizationKpiProcessingResult processingResult() {
    var report = new UtilizationKpiPolicy().calculate(INTERVAL_RUN, intervalReport(), counters());
    return new UtilizationKpiProcessingResult(
        "sha256:" + "b".repeat(64), Instant.parse("2026-09-08T00:00:00Z"), true, report);
  }

  private static EquipmentStateIntervalReport intervalReport() {
    List<StateSignalObservation> observations =
        List.of(
            new StateSignalObservation(
                "Mazak01", SESSION, 0, START, "event-0", StateSignal.EXECUTION, true, "READY"),
            new StateSignalObservation(
                "Mazak01",
                SESSION,
                1,
                START.plusSeconds(300),
                "event-1",
                StateSignal.EXECUTION,
                true,
                "ACTIVE"),
            new StateSignalObservation(
                "Mazak01",
                SESSION,
                2,
                START.plusSeconds(600),
                "event-2",
                StateSignal.CONTROLLER_MODE,
                true,
                "AUTOMATIC"));
    EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();
    return EquipmentStateIntervalReport.of(
        EquipmentStateIntervalPolicy.RULE_VERSION,
        observations,
        policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
  }

  private static List<AccumulatedTimeObservation> counters() {
    return List.of(
        counter(AccumulatedTimeMetric.TOTAL, 0, "100"),
        counter(AccumulatedTimeMetric.AUTO, 0, "20"),
        counter(AccumulatedTimeMetric.CUT, 0, "5"),
        counter(AccumulatedTimeMetric.TOTAL, 2, "300"),
        counter(AccumulatedTimeMetric.AUTO, 2, "120"),
        counter(AccumulatedTimeMetric.CUT, 2, "45"));
  }

  private static AccumulatedTimeObservation counter(
      AccumulatedTimeMetric metric, long sequence, String value) {
    return new AccumulatedTimeObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(sequence * 300),
        "counter-" + metric + "-" + sequence,
        metric,
        true,
        new BigDecimal(value));
  }
}
