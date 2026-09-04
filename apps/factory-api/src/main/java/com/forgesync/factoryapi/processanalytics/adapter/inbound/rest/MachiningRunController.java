package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.FindMachiningRuns;
import com.forgesync.factoryapi.processanalytics.application.SegmentMachiningRuns;
import com.forgesync.factoryapi.processanalytics.application.SegmentMachiningRunsCommand;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunSegmentationPolicy;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/machining-runs")
public final class MachiningRunController {

  public static final String MEDIA_TYPE = "application/vnd.forgesync.machining-runs.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern PROCESSING_RUN_ID = Pattern.compile("sha256:[0-9a-f]{64}");

  private final SegmentMachiningRuns segmentMachiningRuns;
  private final FindMachiningRuns findMachiningRuns;
  private final MachiningRunResponseMapper responseMapper = new MachiningRunResponseMapper();

  public MachiningRunController(
      SegmentMachiningRuns segmentMachiningRuns, FindMachiningRuns findMachiningRuns) {
    this.segmentMachiningRuns = Objects.requireNonNull(segmentMachiningRuns);
    this.findMachiningRuns = Objects.requireNonNull(findMachiningRuns);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  public ResponseEntity<MachiningRunResponse> segment(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    if (request.replaySessionId() == null) {
      throw new InvalidMachiningRunRequestException("replaySessionId is required");
    }
    if (request.throughReplaySequence() < 0) {
      throw new InvalidMachiningRunRequestException("throughReplaySequence must not be negative");
    }
    if (!MachiningRunSegmentationPolicy.RULE_VERSION.equals(request.segmentationRuleVersion())) {
      throw new InvalidMachiningRunRequestException("Unsupported segmentationRuleVersion");
    }
    var result =
        segmentMachiningRuns.segment(
            new SegmentMachiningRunsCommand(
                machineId,
                request.replaySessionId(),
                request.throughReplaySequence(),
                request.segmentationRuleVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(result));
  }

  @GetMapping(produces = MEDIA_TYPE)
  public ResponseEntity<MachiningRunResponse> find(
      @PathVariable String machineId, @RequestParam String processingRunId) {
    requireMachineId(machineId);
    if (!PROCESSING_RUN_ID.matcher(processingRunId).matches()) {
      throw new InvalidMachiningRunRequestException("processingRunId is invalid");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(findMachiningRuns.find(machineId, processingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidMachiningRunRequestException("machineId is invalid");
    }
  }

  public record ProcessingRequest(
      UUID replaySessionId, long throughReplaySequence, String segmentationRuleVersion) {}
}
