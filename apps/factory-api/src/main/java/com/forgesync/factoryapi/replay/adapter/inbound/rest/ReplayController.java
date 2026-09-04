package com.forgesync.factoryapi.replay.adapter.inbound.rest;

import com.forgesync.factoryapi.replay.application.ControlReplay;
import com.forgesync.factoryapi.replay.application.ReplaySessionState;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(ControlReplay.class)
@RequestMapping("/api/v1")
public final class ReplayController {
  public static final String REPLAY_MEDIA_TYPE = "application/vnd.forgesync.replay-session.v1+json";
  private final ControlReplay controlReplay;

  public ReplayController(ControlReplay controlReplay) {
    this.controlReplay = controlReplay;
  }

  @PostMapping(path = "/replay-sessions", produces = REPLAY_MEDIA_TYPE)
  public ResponseEntity<ReplaySessionState> start(@RequestBody StartRequest request) {
    requireIdentifier(request.machineId(), 64, "machineId");
    requireIdentifier(request.sourceSetId(), 128, "sourceSetId");
    return ResponseEntity.status(201)
        .contentType(MediaType.parseMediaType(REPLAY_MEDIA_TYPE))
        .body(
            controlReplay.start(
                request.machineId(), request.sourceSetId(), request.speedMultiplier()));
  }

  @GetMapping(path = "/machines/{machineId}/replay-session", produces = REPLAY_MEDIA_TYPE)
  public ReplaySessionState current(@PathVariable String machineId) {
    requireIdentifier(machineId, 64, "machineId");
    return controlReplay.current(machineId);
  }

  @PostMapping(path = "/replay-sessions/{id}/pause", produces = REPLAY_MEDIA_TYPE)
  public ReplaySessionState pause(@PathVariable UUID id, @RequestBody RevisionRequest request) {
    requireRevision(request.expectedRevision());
    return controlReplay.pause(id, request.expectedRevision());
  }

  @PostMapping(path = "/replay-sessions/{id}/resume", produces = REPLAY_MEDIA_TYPE)
  public ReplaySessionState resume(@PathVariable UUID id, @RequestBody RevisionRequest request) {
    requireRevision(request.expectedRevision());
    return controlReplay.resume(id, request.expectedRevision());
  }

  @PutMapping(path = "/replay-sessions/{id}/speed", produces = REPLAY_MEDIA_TYPE)
  public ReplaySessionState speed(@PathVariable UUID id, @RequestBody SpeedRequest request) {
    requireRevision(request.expectedRevision());
    return controlReplay.changeSpeed(id, request.expectedRevision(), request.speedMultiplier());
  }

  @PostMapping(path = "/replay-sessions/{id}/seek", produces = REPLAY_MEDIA_TYPE)
  public ReplaySessionState seek(@PathVariable UUID id, @RequestBody SeekRequest request) {
    requireRevision(request.expectedRevision());
    if (request.sourceObservedAt() == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "sourceObservedAt is required");
    }
    return controlReplay.seek(
        id, request.expectedRevision(), request.sourceObservedAt(), request.speedMultiplier());
  }

  private static void requireIdentifier(String value, int maxLength, String fieldName) {
    if (value == null || value.length() > maxLength || !value.matches("[A-Za-z0-9._-]+")) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, fieldName + " is invalid");
    }
  }

  private static void requireRevision(long revision) {
    if (revision < 0) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "expectedRevision must not be negative");
    }
  }

  public record StartRequest(String machineId, String sourceSetId, int speedMultiplier) {}

  public record RevisionRequest(long expectedRevision) {}

  public record SpeedRequest(long expectedRevision, int speedMultiplier) {}

  public record SeekRequest(long expectedRevision, Instant sourceObservedAt, int speedMultiplier) {}
}
