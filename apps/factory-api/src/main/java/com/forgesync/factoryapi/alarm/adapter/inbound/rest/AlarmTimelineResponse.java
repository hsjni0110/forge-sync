package com.forgesync.factoryapi.alarm.adapter.inbound.rest;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlarmTimelineResponse(
    String schemaVersion,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    List<AlarmItem> alarms) {
  static AlarmTimelineResponse from(
      String machineId, UUID replaySessionId, long throughReplaySequence, List<Alarm> alarms) {
    return new AlarmTimelineResponse(
        "1.0.0",
        machineId,
        replaySessionId,
        throughReplaySequence,
        alarms.stream().map(AlarmItem::from).toList());
  }

  public record AlarmItem(
      UUID alarmId,
      String machineId,
      UUID replaySessionId,
      long openedReplaySequence,
      Instant openedAt,
      String sourceDataItemId,
      String componentId,
      String conditionType,
      String nativeCode,
      String message,
      String severity,
      String status,
      String ruleVersion,
      long revision,
      String acknowledgedBy,
      Instant acknowledgedAt,
      Long resolvedReplaySequence,
      Instant resolvedAt,
      String source) {
    static AlarmItem from(Alarm alarm) {
      return new AlarmItem(
          alarm.alarmId(),
          alarm.machineId(),
          alarm.replaySessionId(),
          alarm.openedReplaySequence(),
          alarm.openedAt(),
          alarm.sourceDataItemId(),
          alarm.componentId(),
          alarm.conditionType(),
          alarm.nativeCode(),
          alarm.message(),
          alarm.severity().name(),
          alarm.status().name(),
          alarm.ruleVersion(),
          alarm.revision(),
          alarm.acknowledgedBy(),
          alarm.acknowledgedAt(),
          alarm.resolvedReplaySequence(),
          alarm.resolvedAt(),
          "EQUIPMENT_CONDITION");
    }
  }
}
