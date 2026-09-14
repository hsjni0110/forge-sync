package com.forgesync.factoryapi.alarm.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.alarm.application.AlarmRevisionConflictException;
import com.forgesync.factoryapi.alarm.domain.AlarmStatus;
import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import com.forgesync.factoryapi.alarm.domain.ConditionToAlarmPolicy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("database-integration")
@EnabledIfEnvironmentVariable(named = "FORGESYNC_DATABASE_INTEGRATION", matches = "1")
class PostgresAlarmProjectionIntegrationTest {
  private static final UUID SESSION = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final Instant RECORDED_AT = Instant.parse("2026-09-12T01:00:00Z");
  private static JdbcClient jdbcClient;
  private static PostgresAlarmProjection projection;
  private static PostgresAlarmRepository repository;

  @BeforeAll
  static void migrateDatabase() {
    DataSource dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
    projection = new PostgresAlarmProjection(jdbcClient, ConditionToAlarmPolicy.nistMazak01V1());
    repository =
        new PostgresAlarmRepository(
            jdbcClient, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient
        .sql(
            "TRUNCATE business_outbox, alarm, condition_projection, "
                + "canonical_observation_history, ingestion_inbox CASCADE")
        .update();
  }

  @Test
  void keepsConditionSeparateAndOpensOneAlarmAndOutboxEventForRepeatedWarning() {
    ConditionObservation first = condition(1, "warning-1", "WARNING", "345");
    ConditionObservation repeated = condition(2, "warning-2", "WARNING", "345");
    preserveCanonical(first);
    preserveCanonical(repeated);

    projection.project(first, RECORDED_AT);
    projection.project(repeated, RECORDED_AT.plusSeconds(1));
    projection.project(repeated, RECORDED_AT.plusSeconds(1));

    assertThat(rowCount("condition_projection")).isEqualTo(2);
    assertThat(rowCount("alarm")).isEqualTo(1);
    assertThat(rowCount("business_outbox")).isEqualTo(1);
    assertThat(singleText("alarm", "status")).isEqualTo("OPEN");
    assertThat(singleText("business_outbox", "event_type")).isEqualTo("ALARM_OPENED");
  }

  @Test
  void resolvesByNativeCodeAndAllowsASeparateLaterAlarmWhileUnavailableDoesNotResolve() {
    ConditionObservation warning = condition(1, "warning-1", "WARNING", "345");
    ConditionObservation unavailable = condition(2, "unavailable-2", "UNAVAILABLE", null);
    ConditionObservation normal = condition(3, "normal-3", "NORMAL", "345");
    ConditionObservation laterWarning = condition(4, "warning-4", "WARNING", "345");
    preserveCanonical(warning);
    preserveCanonical(unavailable);
    preserveCanonical(normal);
    preserveCanonical(laterWarning);

    projection.project(warning, RECORDED_AT);
    projection.project(unavailable, RECORDED_AT.plusSeconds(1));
    assertThat(singleText("alarm", "status")).isEqualTo("OPEN");
    projection.project(normal, RECORDED_AT.plusSeconds(2));
    projection.project(laterWarning, RECORDED_AT.plusSeconds(3));

    assertThat(rowCount("condition_projection")).isEqualTo(4);
    assertThat(rowCount("alarm")).isEqualTo(2);
    assertThat(rowCount("business_outbox")).isEqualTo(3);
    assertThat(
            jdbcClient
                .sql("SELECT count(*) FROM alarm WHERE status = 'RESOLVED'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void storesNonRuleConditionWithoutCreatingAnAlarm() {
    ConditionObservation condition = condition(1, "warning-1", "WARNING", "9999");
    preserveCanonical(condition);

    projection.project(condition, RECORDED_AT);

    assertThat(rowCount("condition_projection")).isEqualTo(1);
    assertThat(rowCount("alarm")).isZero();
    assertThat(rowCount("business_outbox")).isZero();
  }

  @Test
  void findsThroughCursorAndAcknowledgesOnceWithNamedOperatorAndOutboxEvent() {
    ConditionObservation warning = condition(42, "warning-42", "WARNING", "345");
    preserveCanonical(warning);
    projection.project(warning, RECORDED_AT);
    UUID alarmId = jdbcClient.sql("SELECT alarm_id FROM alarm").query(UUID.class).single();

    assertThat(repository.findThrough("Mazak01", SESSION, 41)).isEmpty();
    assertThat(repository.findThrough("Mazak01", SESSION, 42)).hasSize(1);

    var acknowledged = repository.acknowledge(alarmId, 0, "김 작업자", RECORDED_AT.plusSeconds(1));
    var repeated = repository.acknowledge(alarmId, 0, "다른 작업자", RECORDED_AT.plusSeconds(2));

    assertThat(acknowledged.status()).isEqualTo(AlarmStatus.ACKNOWLEDGED);
    assertThat(acknowledged.acknowledgedBy()).isEqualTo("김 작업자");
    assertThat(repeated).isEqualTo(acknowledged);
    assertThat(rowCount("business_outbox")).isEqualTo(2);
  }

  @Test
  void rejectsAStaleAcknowledgementRevisionWithoutChangingTheAlarm() {
    ConditionObservation warning = condition(42, "warning-42", "WARNING", "345");
    preserveCanonical(warning);
    projection.project(warning, RECORDED_AT);
    UUID alarmId = jdbcClient.sql("SELECT alarm_id FROM alarm").query(UUID.class).single();

    assertThatThrownBy(
            () -> repository.acknowledge(alarmId, 7, "김 작업자", RECORDED_AT.plusSeconds(1)))
        .isInstanceOf(AlarmRevisionConflictException.class);

    assertThat(singleText("alarm", "status")).isEqualTo("OPEN");
    assertThat(rowCount("business_outbox")).isEqualTo(1);
  }

  private static ConditionObservation condition(
      long replaySequence, String sourceEventKey, String level, String nativeCode) {
    return new ConditionObservation(
        "Mazak01",
        SESSION,
        replaySequence,
        Instant.parse("2016-10-05T09:01:00Z").plusSeconds(replaySequence),
        sourceEventKey,
        "Mazak01-controller_2",
        "Mazak01-controller",
        "LOGIC_PROGRAM",
        level,
        nativeCode,
        level.equals("WARNING") ? "ERROR(DOOR OPEN)" : null);
  }

  private static void preserveCanonical(ConditionObservation condition) {
    jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
            VALUES (:session, :source_key, :event_id, :ingested_at);
            INSERT INTO canonical_observation_history (
              event_id, replay_session_id, source_event_key, schema_version, machine_id,
              component_id, observation_kind, source_observed_at, replay_sequence,
              replay_published_at, ingested_at, artifact_id, raw_record_id, mapping_version,
              source_data_item_id, canonical_envelope
            ) VALUES (
              :event_id, :session, :source_key, '2.1.0', :machine,
              :component, 'CONDITION', :source_time, :sequence,
              :recorded_at, :recorded_at, :artifact, :raw_record, '2.2.0',
              :data_item, CAST('{}' AS jsonb)
            )
            """)
        .param("session", condition.replaySessionId())
        .param("source_key", condition.sourceEventKey())
        .param("event_id", UUID.nameUUIDFromBytes(condition.sourceEventKey().getBytes()))
        .param("ingested_at", asUtcOffset(RECORDED_AT))
        .param("machine", condition.machineId())
        .param("component", condition.componentId())
        .param("source_time", asUtcOffset(condition.sourceObservedAt()))
        .param("sequence", condition.replaySequence())
        .param("recorded_at", asUtcOffset(RECORDED_AT))
        .param("artifact", "sha256:" + "a".repeat(64))
        .param("raw_record", "fixture#" + condition.sourceEventKey())
        .param("data_item", condition.sourceDataItemId())
        .update();
  }

  private static long rowCount(String tableName) {
    return jdbcClient.sql("SELECT count(*) FROM " + tableName).query(Long.class).single();
  }

  private static String singleText(String tableName, String columnName) {
    return jdbcClient
        .sql("SELECT " + columnName + " FROM " + tableName)
        .query(String.class)
        .single();
  }

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static DataSource dataSource() {
    return new DriverManagerDataSource(
        System.getenv()
            .getOrDefault("FORGESYNC_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15433/forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_USERNAME", "forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_PASSWORD", "forgesync-test"));
  }
}
