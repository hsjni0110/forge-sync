package com.forgesync.factoryapi.alarm.adapter.outbound.postgres;

import com.forgesync.factoryapi.alarm.application.AlarmObservationParticipant;
import com.forgesync.factoryapi.alarm.domain.Alarm;
import com.forgesync.factoryapi.alarm.domain.AlarmCandidate;
import com.forgesync.factoryapi.alarm.domain.AlarmResolutionScope;
import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import com.forgesync.factoryapi.alarm.domain.ConditionToAlarmPolicy;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresAlarmProjection implements AlarmObservationParticipant {
  private final JdbcClient jdbcClient;
  private final ConditionToAlarmPolicy policy;

  public PostgresAlarmProjection(JdbcClient jdbcClient, ConditionToAlarmPolicy policy) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public void project(ConditionObservation condition, Instant recordedAt) {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(recordedAt);
    if (!preserveCondition(condition, recordedAt)) return;
    resolveMatchingAlarms(condition, recordedAt);
    policy
        .alarmCandidate(condition)
        .ifPresent(candidate -> openUnlessAlreadyActive(candidate, recordedAt));
  }

  private boolean preserveCondition(ConditionObservation condition, Instant recordedAt) {
    return jdbcClient
            .sql(
                """
                INSERT INTO condition_projection (
                  replay_session_id, source_event_key, machine_id, replay_sequence,
                  source_observed_at, source_data_item_id, component_id, condition_type,
                  level, native_code, message, projected_at
                ) VALUES (
                  :session, :source_key, :machine, :sequence,
                  :source_time, :data_item, :component, :condition_type,
                  :level, :native_code, :message, :projected_at
                ) ON CONFLICT (replay_session_id, source_event_key) DO NOTHING
                """)
            .param("session", condition.replaySessionId())
            .param("source_key", condition.sourceEventKey())
            .param("machine", condition.machineId())
            .param("sequence", condition.replaySequence())
            .param("source_time", asUtcOffset(condition.sourceObservedAt()))
            .param("data_item", condition.sourceDataItemId())
            .param("component", condition.componentId())
            .param("condition_type", condition.conditionType())
            .param("level", condition.level())
            .param("native_code", condition.nativeCode(), Types.VARCHAR)
            .param("message", condition.message(), Types.VARCHAR)
            .param("projected_at", asUtcOffset(recordedAt))
            .update()
        == 1;
  }

  private void resolveMatchingAlarms(ConditionObservation condition, Instant recordedAt) {
    AlarmResolutionScope scope = policy.resolutionScope(condition);
    if (scope == AlarmResolutionScope.NONE) return;
    for (Alarm activeAlarm : findActiveAlarms(condition, scope)) {
      Alarm resolved =
          activeAlarm.resolve(
              condition.sourceEventKey(), condition.replaySequence(), condition.sourceObservedAt());
      updateResolvedAlarm(resolved);
      preserveOutboxEvent(resolved, "ALARM_RESOLVED", recordedAt);
    }
  }

  private List<Alarm> findActiveAlarms(
      ConditionObservation condition, AlarmResolutionScope resolutionScope) {
    String nativeCodeClause =
        resolutionScope == AlarmResolutionScope.NATIVE_CODE
            ? " AND native_code = :native_code"
            : "";
    JdbcClient.StatementSpec query =
        jdbcClient
            .sql(
                """
                SELECT * FROM alarm
                WHERE machine_id = :machine AND replay_session_id = :session
                  AND source_data_item_id = :data_item AND status <> 'RESOLVED'
                """
                    + nativeCodeClause
                    + " ORDER BY opened_replay_sequence, alarm_id")
            .param("machine", condition.machineId())
            .param("session", condition.replaySessionId())
            .param("data_item", condition.sourceDataItemId());
    if (resolutionScope == AlarmResolutionScope.NATIVE_CODE) {
      query = query.param("native_code", condition.nativeCode());
    }
    return query.query((resultSet, rowNumber) -> readAlarm(resultSet)).list();
  }

  private void openUnlessAlreadyActive(AlarmCandidate candidate, Instant recordedAt) {
    ConditionObservation condition = candidate.condition();
    if (hasActiveAlarm(condition)) return;
    Alarm alarm = Alarm.open(alarmId(candidate), candidate);
    insertAlarm(alarm);
    preserveOutboxEvent(alarm, "ALARM_OPENED", recordedAt);
  }

  private boolean hasActiveAlarm(ConditionObservation condition) {
    return jdbcClient
        .sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM alarm
              WHERE machine_id = :machine AND replay_session_id = :session
                AND source_data_item_id = :data_item AND native_code = :native_code
                AND status <> 'RESOLVED'
            )
            """)
        .param("machine", condition.machineId())
        .param("session", condition.replaySessionId())
        .param("data_item", condition.sourceDataItemId())
        .param("native_code", condition.nativeCode())
        .query(Boolean.class)
        .single();
  }

  private void insertAlarm(Alarm alarm) {
    jdbcClient
        .sql(
            """
            INSERT INTO alarm (
              alarm_id, machine_id, replay_session_id, opened_replay_sequence, opened_at,
              opened_by_source_event_key, source_data_item_id, component_id, condition_type,
              native_code, message, severity, status, rule_version, revision
            ) VALUES (
              :alarm_id, :machine, :session, :sequence, :opened_at,
              :source_key, :data_item, :component, :condition_type,
              :native_code, :message, :severity, :status, :rule_version, :revision
            )
            """)
        .param("alarm_id", alarm.alarmId())
        .param("machine", alarm.machineId())
        .param("session", alarm.replaySessionId())
        .param("sequence", alarm.openedReplaySequence())
        .param("opened_at", asUtcOffset(alarm.openedAt()))
        .param("source_key", alarm.openedBySourceEventKey())
        .param("data_item", alarm.sourceDataItemId())
        .param("component", alarm.componentId())
        .param("condition_type", alarm.conditionType())
        .param("native_code", alarm.nativeCode())
        .param("message", alarm.message(), Types.VARCHAR)
        .param("severity", alarm.severity().name())
        .param("status", alarm.status().name())
        .param("rule_version", alarm.ruleVersion())
        .param("revision", alarm.revision())
        .update();
  }

  private void updateResolvedAlarm(Alarm alarm) {
    jdbcClient
        .sql(
            """
            UPDATE alarm SET status = :status, revision = :revision,
              resolved_by_source_event_key = :source_key,
              resolved_replay_sequence = :resolved_sequence, resolved_at = :resolved_at
            WHERE alarm_id = :alarm_id
            """)
        .param("status", alarm.status().name())
        .param("revision", alarm.revision())
        .param("source_key", alarm.resolvedBySourceEventKey())
        .param("resolved_sequence", alarm.resolvedReplaySequence())
        .param("resolved_at", asUtcOffset(alarm.resolvedAt()))
        .param("alarm_id", alarm.alarmId())
        .update();
  }

  private void preserveOutboxEvent(Alarm alarm, String eventType, Instant recordedAt) {
    UUID eventId = eventId(alarm, eventType);
    jdbcClient
        .sql(
            """
            INSERT INTO business_outbox (
              event_id, aggregate_type, aggregate_id, event_type, occurred_at, payload
            ) VALUES (
              :event_id, 'ALARM', :alarm_id, :event_type, :occurred_at,
              jsonb_build_object(
                'schemaVersion', '1.0.0', 'eventId', CAST(:event_id AS text),
                'alarmId', CAST(:alarm_id AS text), 'machineId', :machine,
                'replaySessionId', CAST(:session AS text), 'status', :status,
                'severity', :severity, 'revision', :revision,
                'sourceObservedAt', CAST(:source_time AS text),
                'sourceEventKey', :source_key, 'ruleVersion', :rule_version
              )
            ) ON CONFLICT (event_id) DO NOTHING
            """)
        .param("event_id", eventId)
        .param("alarm_id", alarm.alarmId())
        .param("event_type", eventType)
        .param("occurred_at", asUtcOffset(recordedAt))
        .param("machine", alarm.machineId())
        .param("session", alarm.replaySessionId())
        .param("status", alarm.status().name())
        .param("severity", alarm.severity().name())
        .param("revision", alarm.revision())
        .param(
            "source_time",
            asUtcOffset(alarm.resolvedAt() == null ? alarm.openedAt() : alarm.resolvedAt()))
        .param(
            "source_key",
            alarm.resolvedBySourceEventKey() == null
                ? alarm.openedBySourceEventKey()
                : alarm.resolvedBySourceEventKey())
        .param("rule_version", alarm.ruleVersion())
        .update();
  }

  private static Alarm readAlarm(java.sql.ResultSet resultSet) throws java.sql.SQLException {
    OffsetDateTime acknowledgedAt = resultSet.getObject("acknowledged_at", OffsetDateTime.class);
    OffsetDateTime resolvedAt = resultSet.getObject("resolved_at", OffsetDateTime.class);
    Long resolvedReplaySequence = resultSet.getObject("resolved_replay_sequence", Long.class);
    return new Alarm(
        resultSet.getObject("alarm_id", UUID.class),
        resultSet.getString("machine_id"),
        resultSet.getObject("replay_session_id", UUID.class),
        resultSet.getLong("opened_replay_sequence"),
        resultSet.getObject("opened_at", OffsetDateTime.class).toInstant(),
        resultSet.getString("opened_by_source_event_key"),
        resultSet.getString("source_data_item_id"),
        resultSet.getString("component_id"),
        resultSet.getString("condition_type"),
        resultSet.getString("native_code"),
        resultSet.getString("message"),
        com.forgesync.factoryapi.alarm.domain.AlarmSeverity.valueOf(
            resultSet.getString("severity")),
        com.forgesync.factoryapi.alarm.domain.AlarmStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("rule_version"),
        resultSet.getLong("revision"),
        resultSet.getString("acknowledged_by"),
        acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
        resultSet.getString("resolved_by_source_event_key"),
        resolvedReplaySequence,
        resolvedAt == null ? null : resolvedAt.toInstant());
  }

  private static UUID alarmId(AlarmCandidate candidate) {
    ConditionObservation condition = candidate.condition();
    return namedUuid(
        "alarm|"
            + candidate.ruleVersion()
            + "|"
            + condition.replaySessionId()
            + "|"
            + condition.sourceEventKey());
  }

  private static UUID eventId(Alarm alarm, String eventType) {
    return namedUuid("alarm-event|" + alarm.alarmId() + "|" + eventType + "|" + alarm.revision());
  }

  private static UUID namedUuid(String identity) {
    return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
  }

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
