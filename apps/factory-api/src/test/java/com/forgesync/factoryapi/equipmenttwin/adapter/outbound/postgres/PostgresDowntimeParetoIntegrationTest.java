package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoStore.StoredDowntimePareto;
import com.forgesync.factoryapi.equipmenttwin.domain.ConditionEvidenceObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class PostgresDowntimeParetoIntegrationTest {
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private static DataSource dataSource;
  private static JdbcClient jdbcClient;

  @BeforeAll
  static void migrateDatabase() {
    dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient
        .sql(
            """
            TRUNCATE operational_effectiveness_processing,
              downtime_pareto_processing, utilization_kpi_processing,
              equipment_state_interval, equipment_state_interval_processing,
              canonical_observation_history, ingestion_inbox RESTART IDENTITY CASCADE
            """)
        .update();
  }

  @Test
  void readsOnlyWarningAndFaultThroughTheRequestedWatermark() {
    storeCondition(1, "WARNING", "MEMORY PROTECT");
    storeCondition(2, "NORMAL", null);
    storeCondition(3, "FAULT", "DOOR OPEN");

    List<ConditionEvidenceObservation> conditions =
        repository().readAttentionConditions("Mazak01", SESSION, 2);

    assertThat(conditions).hasSize(1);
    assertThat(conditions.getFirst().level()).isEqualTo("WARNING");
    assertThat(conditions.getFirst().message()).isEqualTo("MEMORY PROTECT");
  }

  @Test
  void preservesAndRereadsAnImmutableDowntimeParetoResult() {
    PostgresDowntimeParetoRepository repository = repository();
    DowntimeParetoReport report =
        new DowntimeParetoReport(
            "1.0.0",
            "sha256:" + "b".repeat(64),
            "sha256:" + "a".repeat(64),
            "Mazak01",
            SESSION,
            3,
            START,
            START.plusSeconds(300),
            new BigDecimal("300.000000"),
            "sha256:" + "c".repeat(64),
            "sha256:" + "d".repeat(64),
            List.of());
    StoredDowntimePareto stored =
        new StoredDowntimePareto(
            "sha256:" + "e".repeat(64), Instant.parse("2026-09-10T00:00:00Z"), report);
    storeProcessingReferences(report);

    boolean created = repository.preserve(stored);
    java.util.Optional<StoredDowntimePareto> reread =
        repository.findProcessingRun(stored.processingRunId());

    assertThat(created).isTrue();
    assertThat(reread).contains(stored);
  }

  private static void storeProcessingReferences(DowntimeParetoReport report) {
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_state_interval_processing (
              processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              interval_rule_version, observed_from, observed_to, input_hash,
              input_observation_count, result_hash, coverage, created_at
            ) VALUES (
              :id, :machine, :session, :through, '1.0.0', :from, :to,
              :input_hash, 1, :result_hash, CAST('[]' AS jsonb), :created_at
            )
            """)
        .param("id", report.intervalProcessingRunId())
        .param("machine", report.machineId())
        .param("session", report.replaySessionId())
        .param("through", report.throughReplaySequence())
        .param("from", OffsetDateTime.ofInstant(report.observedFrom(), ZoneOffset.UTC))
        .param("to", OffsetDateTime.ofInstant(report.observedTo(), ZoneOffset.UTC))
        .param("input_hash", "sha256:" + "f".repeat(64))
        .param("result_hash", "sha256:" + "0".repeat(64))
        .param("created_at", OffsetDateTime.parse("2026-09-10T00:00:00Z"))
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO utilization_kpi_processing (
              processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              calculation_version, interval_processing_run_id, observed_from, observed_to,
              input_hash, result_hash, report, created_at
            ) VALUES (
              :id, :machine, :session, :through, '1.0.0', :interval,
              :from, :to, :input_hash, :result_hash, CAST('{}' AS jsonb), :created_at
            )
            """)
        .param("id", report.utilizationProcessingRunId())
        .param("machine", report.machineId())
        .param("session", report.replaySessionId())
        .param("through", report.throughReplaySequence())
        .param("interval", report.intervalProcessingRunId())
        .param("from", OffsetDateTime.ofInstant(report.observedFrom(), ZoneOffset.UTC))
        .param("to", OffsetDateTime.ofInstant(report.observedTo(), ZoneOffset.UTC))
        .param("input_hash", "sha256:" + "1".repeat(64))
        .param("result_hash", "sha256:" + "2".repeat(64))
        .param("created_at", OffsetDateTime.parse("2026-09-10T00:00:00Z"))
        .update();
  }

  private static PostgresDowntimeParetoRepository repository() {
    return new PostgresDowntimeParetoRepository(
        jdbcClient,
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        new ObjectMapper().findAndRegisterModules());
  }

  private static void storeCondition(long sequence, String level, String message) {
    String key = "condition-" + sequence;
    Instant at = START.plusSeconds(sequence);
    String messageField = message == null ? "" : ",\"message\":\"" + message + "\"";
    String envelope =
        "{\"schemaVersion\":\"2.1.0\",\"observationKind\":\"CONDITION\","
            + "\"subject\":{\"componentId\":\"Mazak01-controller\"},"
            + "\"payload\":{\"conditionType\":\"SYSTEM\",\"level\":\""
            + level
            + "\",\"nativeCode\":\"406\""
            + messageField
            + "}}";
    jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
            VALUES (:session, :key, :event_id, :at)
            """)
        .param("session", SESSION)
        .param("key", key)
        .param("event_id", UUID.nameUUIDFromBytes(key.getBytes()))
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO canonical_observation_history (
              event_id, replay_session_id, source_event_key, schema_version, machine_id,
              component_id, observation_kind, source_observed_at, replay_sequence,
              replay_published_at, ingested_at, artifact_id, raw_record_id, mapping_version,
              source_data_item_id, canonical_envelope
            ) VALUES (
              :event_id, :session, :key, '2.1.0', 'Mazak01', 'Mazak01-controller', 'CONDITION',
              :at, :sequence, :at, :at, :artifact, :raw_record, '2.2.0', 'Mazak01-controller_3',
              CAST(:envelope AS jsonb)
            )
            """)
        .param("event_id", UUID.nameUUIDFromBytes(key.getBytes()))
        .param("session", SESSION)
        .param("key", key)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .param("sequence", sequence)
        .param("artifact", "sha256:" + "a".repeat(64))
        .param("raw_record", "raw-" + sequence)
        .param("envelope", envelope)
        .update();
  }

  private static DataSource dataSource() {
    return new DriverManagerDataSource(
        System.getenv()
            .getOrDefault("FORGESYNC_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15432/forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_USERNAME", "forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_PASSWORD", "forgesync-test"));
  }
}
