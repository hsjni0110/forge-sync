package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoCommand;
import com.forgesync.factoryapi.equipmenttwin.application.FindDowntimePareto;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectDowntimePareto;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoPolicy;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = {
      "forgesync.equipment-state-intervals.enabled",
      "forgesync.utilization-kpis.enabled",
      "forgesync.downtime-pareto.enabled"
    },
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/downtime-pareto")
public final class DowntimeParetoController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.downtime-pareto.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern PROCESSING_RUN_ID = Pattern.compile("sha256:[0-9a-f]{64}");

  private final ProjectDowntimePareto projectPareto;
  private final FindDowntimePareto findPareto;
  private final DowntimeParetoResponseMapper responseMapper = new DowntimeParetoResponseMapper();

  public DowntimeParetoController(
      ProjectDowntimePareto projectPareto, FindDowntimePareto findPareto) {
    this.projectPareto = Objects.requireNonNull(projectPareto);
    this.findPareto = Objects.requireNonNull(findPareto);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  public ResponseEntity<DowntimeParetoResponse> project(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    requireProcessingRunId(request.utilizationProcessingRunId(), "utilizationProcessingRunId");
    if (!DowntimeParetoPolicy.RULE_VERSION.equals(request.ruleVersion())) {
      throw new InvalidDowntimeParetoRequestException("Unsupported ruleVersion");
    }
    var result =
        projectPareto.project(
            new DowntimeParetoCommand(
                machineId, request.utilizationProcessingRunId(), request.ruleVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(result));
  }

  @GetMapping(path = "/processing-runs/{processingRunId}", produces = MEDIA_TYPE)
  public ResponseEntity<DowntimeParetoResponse> find(
      @PathVariable String machineId, @PathVariable String processingRunId) {
    requireMachineId(machineId);
    requireProcessingRunId(processingRunId, "processingRunId");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(findPareto.findByProcessingRunId(machineId, processingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidDowntimeParetoRequestException("Invalid machineId");
    }
  }

  private static void requireProcessingRunId(String processingRunId, String fieldName) {
    if (processingRunId == null || !PROCESSING_RUN_ID.matcher(processingRunId).matches()) {
      throw new InvalidDowntimeParetoRequestException("Invalid " + fieldName);
    }
  }

  public record ProcessingRequest(String utilizationProcessingRunId, String ruleVersion) {}
}
