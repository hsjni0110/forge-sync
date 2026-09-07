package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalCommand;
import com.forgesync.factoryapi.equipmenttwin.application.FindEquipmentStateIntervals;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectEquipmentStateIntervals;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import java.util.Objects;
import java.util.UUID;
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
    name = "forgesync.equipment-state-intervals.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/equipment-state-intervals")
public final class EquipmentStateIntervalController {

  public static final String MEDIA_TYPE =
      "application/vnd.forgesync.equipment-state-intervals.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern PROCESSING_RUN_ID = Pattern.compile("sha256:[0-9a-f]{64}");

  private final ProjectEquipmentStateIntervals projectIntervals;
  private final FindEquipmentStateIntervals findIntervals;
  private final EquipmentStateIntervalResponseMapper responseMapper =
      new EquipmentStateIntervalResponseMapper();

  public EquipmentStateIntervalController(
      ProjectEquipmentStateIntervals projectIntervals, FindEquipmentStateIntervals findIntervals) {
    this.projectIntervals = Objects.requireNonNull(projectIntervals);
    this.findIntervals = Objects.requireNonNull(findIntervals);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  public ResponseEntity<EquipmentStateIntervalResponse> project(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    if (request.replaySessionId() == null) {
      throw new InvalidEquipmentStateIntervalRequestException("replaySessionId is required");
    }
    if (request.throughReplaySequence() < 0) {
      throw new InvalidEquipmentStateIntervalRequestException(
          "throughReplaySequence must not be negative");
    }
    if (!EquipmentStateIntervalPolicy.RULE_VERSION.equals(request.intervalRuleVersion())) {
      throw new InvalidEquipmentStateIntervalRequestException("Unsupported intervalRuleVersion");
    }
    var result =
        projectIntervals.project(
            new EquipmentStateIntervalCommand(
                machineId,
                request.replaySessionId(),
                request.throughReplaySequence(),
                request.intervalRuleVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(result));
  }

  @GetMapping(path = "/processing-runs/{processingRunId}", produces = MEDIA_TYPE)
  public ResponseEntity<EquipmentStateIntervalResponse> find(
      @PathVariable String machineId, @PathVariable String processingRunId) {
    requireMachineId(machineId);
    if (!PROCESSING_RUN_ID.matcher(processingRunId).matches()) {
      throw new InvalidEquipmentStateIntervalRequestException("Invalid processingRunId");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(findIntervals.findByProcessingRunId(machineId, processingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidEquipmentStateIntervalRequestException("Invalid machineId");
    }
  }

  public record ProcessingRequest(
      UUID replaySessionId, long throughReplaySequence, String intervalRuleVersion) {}
}
