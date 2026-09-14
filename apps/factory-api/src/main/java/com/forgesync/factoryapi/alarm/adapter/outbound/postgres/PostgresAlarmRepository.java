package com.forgesync.factoryapi.alarm.adapter.outbound.postgres;

import com.forgesync.factoryapi.alarm.application.AlarmNotFoundException;
import com.forgesync.factoryapi.alarm.application.AlarmRepository;
import com.forgesync.factoryapi.alarm.application.AlarmRevisionConflictException;
import com.forgesync.factoryapi.alarm.domain.Alarm;
import com.forgesync.factoryapi.alarm.domain.AlarmSeverity;
import com.forgesync.factoryapi.alarm.domain.AlarmStatus;
import com.forgesync.factoryapi.alarm.domain.InvalidAlarmTransitionException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

public final class PostgresAlarmRepository implements AlarmRepository {
  private final JdbcClient jdbcClient;
  private final TransactionOperations transactions;

  public PostgresAlarmRepository(JdbcClient jdbcClient, TransactionOperations transactions) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactions = Objects.requireNonNull(transactions);
  }

  @Override
  public List<Alarm> findThrough(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM alarm
            WHERE machine_id = :machine AND replay_session_id = :session
              AND opened_replay_sequence <= :through_sequence
            ORDER BY opened_replay_sequence, alarm_id
            """)
        .param("machine", machineId)
        .param("session", replaySessionId)
        .param("through_sequence", throughReplaySequence)
        .query((resultSet, rowNumber) -> readAlarm(resultSet))
        .list();
  }

  @Override
  public Alarm acknowledge(
      UUID alarmId, long expectedRevision, String operatorName, Instant acknowledgedAt) {
    return transactions.execute(
        ignored -> {
          Alarm current =
              jdbcClient
                  .sql("SELECT * FROM alarm WHERE alarm_id = :alarm_id FOR UPDATE")
                  .param("alarm_id", alarmId)
                  .query((resultSet, rowNumber) -> readAlarm(resultSet))
                  .optional()
                  .orElseThrow(() -> new AlarmNotFoundException(alarmId));
          if (current.status() == AlarmStatus.ACKNOWLEDGED) return current;
          if (current.status() == AlarmStatus.RESOLVED) {
            throw new InvalidAlarmTransitionException("A resolved Alarm cannot be acknowledged");
          }
          if (current.revision() != expectedRevision) {
            throw new AlarmRevisionConflictException();
          }
          Alarm acknowledged = current.acknowledge(operatorName, acknowledgedAt);
          updateAcknowledgement(acknowledged);
          preserveAcknowledgedEvent(acknowledged);
          return acknowledged;
        });
  }

  private void updateAcknowledgement(Alarm alarm) {
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE alarm SET status = :status, revision = :next_revision,
                  acknowledged_by = :operator, acknowledged_at = :acknowledged_at
                WHERE alarm_id = :alarm_id AND revision = :expected_revision
                """)
            .param("status", alarm.status().name())
            .param("next_revision", alarm.revision())
            .param("operator", alarm.acknowledgedBy())
            .param("acknowledged_at", asUtcOffset(alarm.acknowledgedAt()))
            .param("alarm_id", alarm.alarmId())
            .param("expected_revision", alarm.revision() - 1)
            .update();
    if (updated != 1) throw new AlarmRevisionConflictException();
  }

  private void preserveAcknowledgedEvent(Alarm alarm) {
    UUID eventId =
        UUID.nameUUIDFromBytes(
            ("alarm-event|" + alarm.alarmId() + "|ALARM_ACKNOWLEDGED|" + alarm.revision())
                .getBytes(StandardCharsets.UTF_8));
    jdbcClient
        .sql(
            """
            INSERT INTO business_outbox (
              event_id, aggregate_type, aggregate_id, event_type, occurred_at, payload
            ) VALUES (
              :event_id, 'ALARM', :alarm_id, 'ALARM_ACKNOWLEDGED', :occurred_at,
              jsonb_build_object(
                'schemaVersion', '1.0.0', 'eventId', CAST(:event_id AS text),
                'alarmId', CAST(:alarm_id AS text), 'machineId', :machine,
                'replaySessionId', CAST(:session AS text), 'status', :status,
                'severity', :severity, 'revision', :revision,
                'acknowledgedBy', :operator,
                'acknowledgedAt', CAST(:acknowledged_at AS text),
                'ruleVersion', :rule_version
              )
            ) ON CONFLICT (event_id) DO NOTHING
            """)
        .param("event_id", eventId)
        .param("alarm_id", alarm.alarmId())
        .param("occurred_at", asUtcOffset(alarm.acknowledgedAt()))
        .param("machine", alarm.machineId())
        .param("session", alarm.replaySessionId())
        .param("status", alarm.status().name())
        .param("severity", alarm.severity().name())
        .param("revision", alarm.revision())
        .param("operator", alarm.acknowledgedBy())
        .param("acknowledged_at", asUtcOffset(alarm.acknowledgedAt()))
        .param("rule_version", alarm.ruleVersion())
        .update();
  }

  private static Alarm readAlarm(java.sql.ResultSet resultSet) throws java.sql.SQLException {
    OffsetDateTime acknowledgedAt = resultSet.getObject("acknowledged_at", OffsetDateTime.class);
    OffsetDateTime resolvedAt = resultSet.getObject("resolved_at", OffsetDateTime.class);
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
        AlarmSeverity.valueOf(resultSet.getString("severity")),
        AlarmStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("rule_version"),
        resultSet.getLong("revision"),
        resultSet.getString("acknowledged_by"),
        acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
        resultSet.getString("resolved_by_source_event_key"),
        resultSet.getObject("resolved_replay_sequence", Long.class),
        resolvedAt == null ? null : resolvedAt.toInstant());
  }

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
