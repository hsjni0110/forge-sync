package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.application.FindDowntimePareto;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectDowntimePareto;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DowntimeParetoControllerTest {
  private static final String UTILIZATION_RUN = "sha256:" + "b".repeat(64);

  @Test
  void createsAProjectionFromAnExplicitImmutableUtilizationReference() {
    DowntimeParetoProcessingResult result = processingResult();
    ProjectDowntimePareto project = command -> result;
    FindDowntimePareto find = (machineId, processingRunId) -> result.asExisting();
    DowntimeParetoController controller = new DowntimeParetoController(project, find);

    var response =
        controller.project(
            "Mazak01",
            new DowntimeParetoController.ProcessingRequest(
                UTILIZATION_RUN, DowntimeParetoPolicy.RULE_VERSION));

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo(DowntimeParetoController.MEDIA_TYPE);
  }

  @Test
  void rejectsAnUnsupportedRuleVersionBeforeProjection() {
    ProjectDowntimePareto project = command -> processingResult();
    FindDowntimePareto find = (machineId, processingRunId) -> processingResult();
    DowntimeParetoController controller = new DowntimeParetoController(project, find);

    assertThatThrownBy(
            () ->
                controller.project(
                    "Mazak01",
                    new DowntimeParetoController.ProcessingRequest(UTILIZATION_RUN, "2.0.0")))
        .isInstanceOf(InvalidDowntimeParetoRequestException.class)
        .hasMessageContaining("ruleVersion");
  }

  private static DowntimeParetoProcessingResult processingResult() {
    var report =
        new DowntimeParetoReport(
            DowntimeParetoPolicy.RULE_VERSION,
            UTILIZATION_RUN,
            "sha256:" + "a".repeat(64),
            "Mazak01",
            UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac"),
            3,
            Instant.parse("2016-10-05T09:00:00Z"),
            Instant.parse("2016-10-05T09:01:00Z"),
            new BigDecimal("60.000000"),
            "sha256:" + "c".repeat(64),
            "sha256:" + "d".repeat(64),
            List.of());
    return new DowntimeParetoProcessingResult(
        "sha256:" + "e".repeat(64), Instant.parse("2026-09-10T00:00:00Z"), true, report);
  }
}
