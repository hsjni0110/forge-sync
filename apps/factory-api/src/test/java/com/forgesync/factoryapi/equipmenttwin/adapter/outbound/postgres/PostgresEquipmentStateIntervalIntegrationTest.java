package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalCommand;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalService;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateInterval;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import java.time.Clock;
import java.time.Duration;
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
class PostgresEquipmentStateIntervalIntegrationTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static JdbcClient jdbcClient;
  private static EquipmentStateIntervalService service;

  @BeforeAll
  static void migrateDatabase() {
    DataSource dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
    PostgresEquipmentStateIntervalRepository repository =
        new PostgresEquipmentStateIntervalRepository(
            jdbcClient,
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            OBJECT_MAPPER);
    service =
        new EquipmentStateIntervalService(
            repository,
            repository,
            new EquipmentStateIntervalPolicy(),
            Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient
        .sql(
            """
            TRUNCATE operational_effectiveness_processing,
              equipment_state_interval, equipment_state_interval_processing,
              canonical_observation_history, ingestion_inbox RESTART IDENTITY CASCADE
            """)
        .update();
  }

  @Test
  void reusesTheStoredResultWhenTheSameInputIsProjectedAgain() {
    storeExecution(0, START, "READY");
    storeExecution(1, START.plusSeconds(90), "ACTIVE");

    EquipmentStateIntervalProcessingResult first = project(1);
    EquipmentStateIntervalProcessingResult second = project(1);

    assertThat(first.isCreated()).isTrue();
    assertThat(second.isCreated()).isFalse();
    assertThat(second.processingRunId()).isEqualTo(first.processingRunId());
    assertThat(second.report().resultHash()).isEqualTo(first.report().resultHash());
    assertThat(rowCount("equipment_state_interval_processing")).isEqualTo(1);
  }

  @Test
  void latecomingObservationsLeaveTheEarlierIntervalsExactlyAsTheyWere() {
    storeExecution(0, START, "READY");
    storeExecution(1, START.plusSeconds(90), "ACTIVE");
    EquipmentStateIntervalProcessingResult before = project(1);
    List<EquipmentStateInterval> beforeIntervals = before.report().intervals();

    storeExecution(2, START.plusSeconds(240), "STOPPED");
    EquipmentStateIntervalProcessingResult after = project(2);

    assertThat(after.processingRunId()).isNotEqualTo(before.processingRunId());
    assertThat(rowCount("equipment_state_interval_processing")).isEqualTo(2);
    EquipmentStateIntervalProcessingResult reread =
        service.findByProcessingRunId("Mazak01", before.processingRunId());
    assertThat(reread.report().resultHash()).isEqualTo(before.report().resultHash());
    assertThat(reread.report().intervals()).isEqualTo(beforeIntervals);
    assertThat(reread.report().intervals().get(1).isOpen()).isTrue();
    assertThat(after.report().intervals().get(1).endedAt()).isEqualTo(START.plusSeconds(240));
  }

  @Test
  void keepsBoundaryEvidenceAndCoverageAcrossAStoreAndRead() {
    storeExecution(0, START, "READY");
    storeExecution(1, START.plusSeconds(90), "ACTIVE");
    storeUnavailable(2, START.plusSeconds(150), "CONTROLLER_MODE");

    String processingRunId = project(2).processingRunId();
    EquipmentStateIntervalProcessingResult reread =
        service.findByProcessingRunId("Mazak01", processingRunId);

    EquipmentStateInterval closed = reread.report().intervals().get(0);
    assertThat(closed.startEvidence().sourceEventKey()).isEqualTo("event-0");
    assertThat(closed.endEvidence().sourceEventKey()).isEqualTo("event-1");
    assertThat(closed.duration()).contains(Duration.ofSeconds(90));
    assertThat(reread.report().coverageOf(StateSignal.EXECUTION).closedDuration())
        .isEqualTo(Duration.ofSeconds(90));
    assertThat(reread.report().intervals())
        .filteredOn(interval -> interval.signal() == StateSignal.CONTROLLER_MODE)
        .singleElement()
        .matches(EquipmentStateInterval::isUnknown);
  }

  @Test
  void intervalTotalsNeverExceedTheObservedRange() {
    storeExecution(0, START, "READY");
    storeExecution(1, START.plusSeconds(90), "ACTIVE");
    storeExecution(2, START.plusSeconds(240), "STOPPED");

    EquipmentStateIntervalProcessingResult result = project(2);

    assertThat(result.report().coverage())
        .allSatisfy(
            coverage ->
                assertThat(coverage.closedDuration())
                    .isLessThanOrEqualTo(result.report().observedRange()));
  }

  private EquipmentStateIntervalProcessingResult project(long throughReplaySequence) {
    return service.project(
        new EquipmentStateIntervalCommand(
            "Mazak01", SESSION, throughReplaySequence, EquipmentStateIntervalPolicy.RULE_VERSION));
  }

  private void storeExecution(long sequence, Instant at, String value) {
    storeObservation(sequence, at, "EXECUTION", "\"value\":\"" + value + "\"", "AVAILABLE");
  }

  private void storeUnavailable(long sequence, Instant at, String eventType) {
    storeObservation(sequence, at, eventType, null, "UNAVAILABLE");
  }

  private void storeObservation(
      long sequence, Instant at, String eventType, String valueField, String availability) {
    String sourceEventKey = "event-" + sequence;
    String envelope =
        "{\"schemaVersion\":\"2.1.0\",\"payload\":{\"eventType\":\""
            + eventType
            + "\",\"availability\":\""
            + availability
            + "\""
            + (valueField == null ? "" : "," + valueField)
            + "}}";
    jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
            VALUES (:replay_session_id, :source_event_key, :event_id, :at)
            """)
        .param("replay_session_id", SESSION)
        .param("source_event_key", sourceEventKey)
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
              :event_id, :replay_session_id, :source_event_key, '2.1.0', 'Mazak01',
              'Mazak01-path', 'EVENT', :at, :sequence,
              :at, :at, :artifact, :raw_record, '2.2.0',
              'Mazak01-path_13', CAST(:envelope AS jsonb)
            )
            """)
        .param("event_id", UUID.nameUUIDFromBytes(sourceEventKey.getBytes()))
        .param("replay_session_id", SESSION)
        .param("source_event_key", sourceEventKey)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .param("sequence", sequence)
        .param("artifact", "sha256:" + "a".repeat(64))
        .param("raw_record", "raw-" + sequence)
        .param("envelope", envelope)
        .update();
  }

  private static long rowCount(String tableName) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName).query(Long.class).single();
  }

  private static DataSource dataSource() {
    return new DriverManagerDataSource(
        System.getenv()
            .getOrDefault("FORGESYNC_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15432/forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_USERNAME", "forgesync"),
        System.getenv().getOrDefault("FORGESYNC_DATABASE_PASSWORD", "forgesync-test"));
  }
}
