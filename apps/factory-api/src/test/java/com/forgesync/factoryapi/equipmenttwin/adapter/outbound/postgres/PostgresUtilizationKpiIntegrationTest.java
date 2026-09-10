package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiStore.StoredUtilizationKpi;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeMetric;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiReport;
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
class PostgresUtilizationKpiIntegrationTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  private static JdbcClient jdbcClient;
  private static DataSource dataSource;

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
              utilization_kpi_processing, equipment_state_interval,
              equipment_state_interval_processing, canonical_observation_history,
              ingestion_inbox RESTART IDENTITY CASCADE
            """)
        .update();
  }

  @Test
  void readsUncorrectedCountersOnlyThroughTheRequestedReplayWatermark() {
    storeCounter(0, AccumulatedTimeMetric.TOTAL, "100");
    storeCounter(1, AccumulatedTimeMetric.AUTO, "20");
    storeCounter(2, AccumulatedTimeMetric.TOTAL, "50");
    PostgresUtilizationKpiRepository repository = repository();

    List<AccumulatedTimeObservation> observations =
        repository.readAccumulatedTimes("Mazak01", SESSION, 1L);

    assertThat(observations).hasSize(2);
    assertThat(observations)
        .extracting(AccumulatedTimeObservation::metric)
        .containsExactly(AccumulatedTimeMetric.TOTAL, AccumulatedTimeMetric.AUTO);
    assertThat(observations.get(0).valueSeconds().toString()).isEqualTo("100");
  }

  @Test
  void preservesAndRereadsAnImmutableUtilizationProcessingResult() {
    PostgresUtilizationKpiRepository repository = repository();
    UtilizationKpiReport report =
        new UtilizationKpiPolicy()
            .calculate("sha256:" + "a".repeat(64), intervalReport(), counterFixture());
    storeIntervalProcessingReference(report);
    StoredUtilizationKpi stored =
        new StoredUtilizationKpi(
            "sha256:" + "b".repeat(64), Instant.parse("2026-09-08T00:00:00Z"), report);

    boolean created = repository.preserve(stored);
    java.util.Optional<StoredUtilizationKpi> reread =
        repository.findProcessingRun(stored.processingRunId());

    assertThat(created).isTrue();
    assertThat(reread).contains(stored);
  }

  private static void storeIntervalProcessingReference(UtilizationKpiReport report) {
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
        .param("input_hash", "sha256:" + "c".repeat(64))
        .param("result_hash", "sha256:" + "d".repeat(64))
        .param("created_at", OffsetDateTime.parse("2026-09-08T00:00:00Z"))
        .update();
  }

  private static PostgresUtilizationKpiRepository repository() {
    return new PostgresUtilizationKpiRepository(
        jdbcClient,
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        new ObjectMapper().findAndRegisterModules());
  }

  private static EquipmentStateIntervalReport intervalReport() {
    List<StateSignalObservation> observations =
        List.of(
            new StateSignalObservation(
                "Mazak01", SESSION, 0, START, "event-0", StateSignal.EXECUTION, true, "READY"),
            new StateSignalObservation(
                "Mazak01",
                SESSION,
                1,
                START.plusSeconds(300),
                "event-1",
                StateSignal.EXECUTION,
                true,
                "ACTIVE"),
            new StateSignalObservation(
                "Mazak01",
                SESSION,
                2,
                START.plusSeconds(600),
                "event-2",
                StateSignal.CONTROLLER_MODE,
                true,
                "AUTOMATIC"));
    EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();
    return EquipmentStateIntervalReport.of(
        EquipmentStateIntervalPolicy.RULE_VERSION,
        observations,
        policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
  }

  private static List<AccumulatedTimeObservation> counterFixture() {
    return List.of(
        counter(AccumulatedTimeMetric.TOTAL, 0, "100"),
        counter(AccumulatedTimeMetric.AUTO, 0, "20"),
        counter(AccumulatedTimeMetric.CUT, 0, "5"),
        counter(AccumulatedTimeMetric.TOTAL, 2, "300"),
        counter(AccumulatedTimeMetric.AUTO, 2, "120"),
        counter(AccumulatedTimeMetric.CUT, 2, "45"));
  }

  private static AccumulatedTimeObservation counter(
      AccumulatedTimeMetric metric, long sequence, String value) {
    return new AccumulatedTimeObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(sequence * 300),
        "fixture-" + metric + "-" + sequence,
        metric,
        true,
        new java.math.BigDecimal(value));
  }

  private static void storeCounter(long sequence, AccumulatedTimeMetric metric, String value) {
    String sourceEventKey = "counter-" + sequence;
    Instant at = START.plusSeconds(sequence);
    String canonicalMetric = metric.name() + "_ACCUMULATED_TIME";
    String envelope =
        "{\"schemaVersion\":\"2.1.0\",\"payload\":{\"metric\":\""
            + canonicalMetric
            + "\",\"availability\":\"AVAILABLE\",\"value\":"
            + value
            + ",\"unit\":\"SECOND\",\"unitProvenance\":\"DERIVED\"}}";
    jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
            VALUES (:session, :key, :event_id, :at)
            """)
        .param("session", SESSION)
        .param("key", sourceEventKey)
        .param("event_id", UUID.nameUUIDFromBytes(sourceEventKey.getBytes()))
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
              :event_id, :session, :key, '2.1.0', 'Mazak01', 'Mazak01-path', 'SAMPLE',
              :at, :sequence, :at, :at, :artifact, :raw_record, '2.2.0', :data_item,
              CAST(:envelope AS jsonb)
            )
            """)
        .param("event_id", UUID.nameUUIDFromBytes(sourceEventKey.getBytes()))
        .param("session", SESSION)
        .param("key", sourceEventKey)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .param("sequence", sequence)
        .param("artifact", "sha256:" + "a".repeat(64))
        .param("raw_record", "raw-" + sequence)
        .param("data_item", metric.name().toLowerCase() + "_time")
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
