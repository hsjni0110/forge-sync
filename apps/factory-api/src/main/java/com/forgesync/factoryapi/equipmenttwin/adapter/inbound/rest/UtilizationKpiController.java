package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.FindUtilizationKpis;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectUtilizationKpis;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiCommand;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
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
    name = {"forgesync.equipment-state-intervals.enabled", "forgesync.utilization-kpis.enabled"},
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/utilization-kpis")
public final class UtilizationKpiController {

  public static final String MEDIA_TYPE = "application/vnd.forgesync.utilization-kpis.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern PROCESSING_RUN_ID = Pattern.compile("sha256:[0-9a-f]{64}");

  private final ProjectUtilizationKpis projectKpis;
  private final FindUtilizationKpis findKpis;
  private final UtilizationKpiResponseMapper responseMapper = new UtilizationKpiResponseMapper();

  public UtilizationKpiController(
      ProjectUtilizationKpis projectKpis, FindUtilizationKpis findKpis) {
    this.projectKpis = Objects.requireNonNull(projectKpis);
    this.findKpis = Objects.requireNonNull(findKpis);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  public ResponseEntity<UtilizationKpiResponse> project(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    requireProcessingRunId(request.intervalProcessingRunId(), "intervalProcessingRunId");
    if (!UtilizationKpiPolicy.RULE_VERSION.equals(request.calculationVersion())) {
      throw new InvalidUtilizationKpiRequestException("Unsupported calculationVersion");
    }
    var result =
        projectKpis.project(
            new UtilizationKpiCommand(
                machineId, request.intervalProcessingRunId(), request.calculationVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(result));
  }

  @GetMapping(path = "/processing-runs/{processingRunId}", produces = MEDIA_TYPE)
  public ResponseEntity<UtilizationKpiResponse> find(
      @PathVariable String machineId, @PathVariable String processingRunId) {
    requireMachineId(machineId);
    requireProcessingRunId(processingRunId, "processingRunId");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(findKpis.findByProcessingRunId(machineId, processingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidUtilizationKpiRequestException("Invalid machineId");
    }
  }

  private static void requireProcessingRunId(String processingRunId, String fieldName) {
    if (processingRunId == null || !PROCESSING_RUN_ID.matcher(processingRunId).matches()) {
      throw new InvalidUtilizationKpiRequestException("Invalid " + fieldName);
    }
  }

  public record ProcessingRequest(String intervalProcessingRunId, String calculationVersion) {}
}
