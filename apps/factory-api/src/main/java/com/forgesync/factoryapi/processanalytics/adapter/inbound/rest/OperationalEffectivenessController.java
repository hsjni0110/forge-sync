package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.FindOperationalEffectiveness;
import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessCommand;
import com.forgesync.factoryapi.processanalytics.application.ProjectOperationalEffectiveness;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessPolicy;
import java.math.BigDecimal;
import java.util.Map;
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
    name = {"forgesync.process-analytics.enabled", "forgesync.utilization-kpis.enabled"},
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/operational-effectiveness")
public final class OperationalEffectivenessController {
  public static final String MEDIA_TYPE =
      "application/vnd.forgesync.operational-effectiveness.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern HASH_ID = Pattern.compile("sha256:[0-9a-f]{64}");
  private final ProjectOperationalEffectiveness project;
  private final FindOperationalEffectiveness find;
  private final OperationalEffectivenessResponseMapper mapper =
      new OperationalEffectivenessResponseMapper();

  public OperationalEffectivenessController(
      ProjectOperationalEffectiveness project, FindOperationalEffectiveness find) {
    this.project = Objects.requireNonNull(project);
    this.find = Objects.requireNonNull(find);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  ResponseEntity<OperationalEffectivenessResponse> project(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    validate(machineId, request);
    var result =
        project.project(
            new OperationalEffectivenessCommand(
                machineId,
                request.utilizationProcessingRunId(),
                request.cycleFeatureProcessingRunId(),
                request.policyVersion(),
                request.assumedIdealCycleSecondsByProgram()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(mapper.map(result));
  }

  @GetMapping(path = "/processing-runs/{processingRunId}", produces = MEDIA_TYPE)
  ResponseEntity<OperationalEffectivenessResponse> find(
      @PathVariable String machineId, @PathVariable String processingRunId) {
    if (!MACHINE_ID.matcher(machineId).matches() || !HASH_ID.matcher(processingRunId).matches()) {
      throw new InvalidOperationalEffectivenessRequestException("Invalid identity");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(mapper.map(find.find(machineId, processingRunId)));
  }

  private static void validate(String machineId, ProcessingRequest request) {
    if (!MACHINE_ID.matcher(machineId).matches()
        || request == null
        || request.utilizationProcessingRunId() == null
        || !HASH_ID.matcher(request.utilizationProcessingRunId()).matches()
        || request.cycleFeatureProcessingRunId() == null
        || !HASH_ID.matcher(request.cycleFeatureProcessingRunId()).matches()
        || !OperationalEffectivenessPolicy.POLICY_VERSION.equals(request.policyVersion())) {
      throw new InvalidOperationalEffectivenessRequestException("Invalid processing request");
    }
    Map<String, BigDecimal> assumptions = request.assumedIdealCycleSecondsByProgram();
    if (assumptions != null
        && assumptions.entrySet().stream()
            .anyMatch(
                entry ->
                    entry.getKey() == null
                        || entry.getKey().isBlank()
                        || entry.getValue() == null
                        || entry.getValue().signum() <= 0)) {
      throw new InvalidOperationalEffectivenessRequestException(
          "Assumed ideal cycle seconds must be positive and program-specific");
    }
  }

  public record ProcessingRequest(
      String utilizationProcessingRunId,
      String cycleFeatureProcessingRunId,
      String policyVersion,
      Map<String, BigDecimal> assumedIdealCycleSecondsByProgram) {}
}
