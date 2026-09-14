package com.forgesync.factoryapi.alarm.adapter.inbound.rest;

import com.forgesync.factoryapi.alarm.application.AcknowledgeAlarm;
import com.forgesync.factoryapi.alarm.application.FindAlarms;
import java.util.UUID;
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
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public final class AlarmController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.alarms.v1+json";
  private final FindAlarms findAlarms;
  private final AcknowledgeAlarm acknowledgeAlarm;

  public AlarmController(FindAlarms findAlarms, AcknowledgeAlarm acknowledgeAlarm) {
    this.findAlarms = findAlarms;
    this.acknowledgeAlarm = acknowledgeAlarm;
  }

  @GetMapping(path = "/machines/{machineId}/alarms", produces = MEDIA_TYPE)
  public AlarmTimelineResponse find(
      @PathVariable String machineId,
      @RequestParam UUID replaySessionId,
      @RequestParam long throughReplaySequence) {
    return AlarmTimelineResponse.from(
        machineId,
        replaySessionId,
        throughReplaySequence,
        findAlarms.find(machineId, replaySessionId, throughReplaySequence));
  }

  @PostMapping(path = "/alarms/{alarmId}/acknowledge", produces = MEDIA_TYPE)
  public ResponseEntity<AlarmTimelineResponse.AlarmItem> acknowledge(
      @PathVariable UUID alarmId, @RequestBody AcknowledgeRequest request) {
    var alarm =
        acknowledgeAlarm.acknowledge(alarmId, request.expectedRevision(), request.operatorName());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(AlarmTimelineResponse.AlarmItem.from(alarm));
  }

  public record AcknowledgeRequest(long expectedRevision, String operatorName) {}
}
