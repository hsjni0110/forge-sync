package com.forgesync.factoryapi.toolchange.adapter.inbound.rest;

import com.forgesync.factoryapi.toolchange.application.FindToolChanges;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/tool-changes")
public final class ToolChangeTimelineController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.tool-changes.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private final FindToolChanges findToolChanges;

  public ToolChangeTimelineController(FindToolChanges findToolChanges) {
    this.findToolChanges = findToolChanges;
  }

  @GetMapping(produces = MEDIA_TYPE)
  public ResponseEntity<ToolChangeTimelineResponse> find(
      @PathVariable String machineId,
      @RequestParam UUID replaySessionId,
      @RequestParam long throughReplaySequence) {
    if (!MACHINE_ID.matcher(machineId).matches() || throughReplaySequence < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "invalid tool change timeline request");
    }
    var changes = findToolChanges.find(machineId, replaySessionId, throughReplaySequence);
    var items =
        changes.stream()
            .map(
                change ->
                    new ToolChangeTimelineResponse.ToolChangeItem(
                        change.replaySequence(),
                        change.sourceObservedAt(),
                        change.fromToolNumber(),
                        change.toToolNumber(),
                        new ToolChangeTimelineResponse.Provenance(
                            new ToolChangeTimelineResponse.Source(
                                "REAL", "NIST", change.sourceSetId(), change.artifactId()),
                            new ToolChangeTimelineResponse.Transformation(
                                change.rawRecordId(),
                                change.mappingVersion(),
                                change.sourceDataItemId()))))
            .toList();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(
            new ToolChangeTimelineResponse(
                "1.0.0", machineId, replaySessionId, throughReplaySequence, items));
  }
}
