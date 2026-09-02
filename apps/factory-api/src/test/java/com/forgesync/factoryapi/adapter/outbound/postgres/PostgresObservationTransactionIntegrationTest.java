package com.forgesync.factoryapi.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forgesync.factoryapi.adapter.inbound.mqtt.MqttObservationPacket;
import com.forgesync.factoryapi.adapter.inbound.mqtt.MqttObservationValidator;
import com.forgesync.factoryapi.adapter.inbound.observation.ObservationContractValidator;
import com.forgesync.factoryapi.application.IngestionResult;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("database-integration")
@EnabledIfEnvironmentVariable(named = "FORGESYNC_DATABASE_INTEGRATION", matches = "1")
class PostgresObservationTransactionIntegrationTest {

  private static final Instant INGESTED_AT = Instant.parse("2026-09-02T01:02:03Z");
  private static final UUID REPLAY_SESSION_ID =
      UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static JdbcClient jdbcClient;
  private static PostgresObservationTransaction transaction;

  @BeforeAll
  static void migrateDatabase() {
    DataSource dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
    transaction =
        new PostgresObservationTransaction(
            jdbcClient, new DataSourceTransactionManager(dataSource));
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient.sql("TRUNCATE canonical_observation_history, ingestion_inbox").update();
  }

  @Test
  void acceptsOneOfOneHundredConcurrentDuplicateDeliveries() throws Exception {
    ValidatedObservationMessage observation = replayedObservation("event-execution.json", 42);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<IngestionResult>> futures = new ArrayList<>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int attempt = 0; attempt < 100; attempt++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return transaction.storeObservation(observation, INGESTED_AT);
                }));
      }
      start.countDown();

      List<IngestionResult> results = new ArrayList<>();
      for (Future<IngestionResult> future : futures) {
        results.add(future.get());
      }

      assertThat(results).containsOnlyOnce(IngestionResult.ACCEPTED);
      assertThat(results.stream().filter(IngestionResult.SKIPPED_DUPLICATE::equals)).hasSize(99);
    }

    assertThat(rowCount("ingestion_inbox")).isEqualTo(1);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(1);
  }

  @Test
  void rollsBackInboxWhenObservationInsertFails() {
    ValidatedObservationMessage accepted = replayedObservation("event-execution.json", 42);
    assertThat(transaction.storeObservation(accepted, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    String conflictingSourceEventKey = accepted.sourceEventKey() + "#conflict";
    ValidatedObservationMessage conflicting =
        copyWithInvalidKindAndSourceEventKey(accepted, conflictingSourceEventKey);

    assertThatThrownBy(() -> transaction.storeObservation(conflicting, INGESTED_AT))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(rowCount("ingestion_inbox")).isEqualTo(1);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(1);
    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT COUNT(*) FROM ingestion_inbox
                    WHERE replay_session_id = :replay_session_id
                      AND source_event_key = :source_event_key
                    """)
                .param("replay_session_id", accepted.replaySessionId())
                .param("source_event_key", conflictingSourceEventKey)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void acceptsSameCanonicalEventInDifferentReplaySessions() {
    ValidatedObservationMessage firstReplay = replayedObservation("event-execution.json", 42);
    UUID anotherReplaySession = UUID.fromString("5dfd98c9-532e-43a7-ac9f-31b2db874920");
    ValidatedObservationMessage secondReplay =
        replayedObservation("event-execution.json", 42, anotherReplaySession);

    assertThat(transaction.storeObservation(firstReplay, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(secondReplay, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);

    assertThat(firstReplay.eventId()).isEqualTo(secondReplay.eventId());
    assertThat(rowCount("ingestion_inbox")).isEqualTo(2);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(2);
  }

  @Test
  void preservesCanonicalCategoryTimesAndProvenanceForEveryVariant() {
    ValidatedObservationMessage sample = replayedObservation("sample-spindle-speed.json", 1);
    ValidatedObservationMessage event = replayedObservation("event-execution.json", 2);
    ValidatedObservationMessage condition = replayedObservation("condition-warning.json", 3);

    assertThat(transaction.storeObservation(sample, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(event, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(condition, INGESTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);

    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT observation_kind FROM canonical_observation_history
                    ORDER BY replay_sequence
                    """)
                .query(String.class)
                .list())
        .containsExactly("SAMPLE", "EVENT", "CONDITION");
    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT COUNT(*) FROM canonical_observation_history
                    WHERE source_observed_at IS NOT NULL
                      AND replay_published_at IS NOT NULL
                      AND ingested_at = :ingested_at
                      AND raw_record_id <> ''
                      AND source_data_item_id <> ''
                      AND canonical_envelope -> 'provenance' IS NOT NULL
                    """)
                .param("ingested_at", OffsetDateTime.ofInstant(INGESTED_AT, ZoneOffset.UTC))
                .query(Long.class)
                .single())
        .isEqualTo(3);
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

  private static ValidatedObservationMessage replayedObservation(
      String fixtureName, long replaySequence) {
    return replayedObservation(fixtureName, replaySequence, REPLAY_SESSION_ID);
  }

  private static ValidatedObservationMessage replayedObservation(
      String fixtureName, long replaySequence, UUID replaySessionId) {
    ObjectNode document = (ObjectNode) readFixture(fixtureName);
    ObjectNode replay = OBJECT_MAPPER.createObjectNode();
    replay.put("replaySessionId", replaySessionId.toString());
    replay.put("replaySequence", replaySequence);
    replay.put("replayPublishedAt", "2026-09-01T00:00:00Z");
    document.set("replay", replay);
    String json = document.toString();
    String machineId = document.path("machineId").asText();
    String sourceEventKey = document.path("sourceEventKey").asText();
    MqttObservationPacket packet =
        new MqttObservationPacket(
            "forgesync/observations/" + machineId,
            json.getBytes(StandardCharsets.UTF_8),
            1,
            false,
            "application/vnd.forgesync.observation+json",
            1,
            Map.of(
                "schema-version", List.of("2.0.0"),
                "message-key", List.of(replaySessionId + ":" + sourceEventKey)));
    return new MqttObservationValidator(new ObservationContractValidator(), OBJECT_MAPPER)
        .validate(packet);
  }

  private static JsonNode readFixture(String fixtureName) {
    String path = "fixtures/canonical/v2/valid/" + fixtureName;
    try (InputStream stream =
        PostgresObservationTransactionIntegrationTest.class
            .getClassLoader()
            .getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Fixture is not packaged: " + path);
      }
      return OBJECT_MAPPER.readTree(stream);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read fixture", exception);
    }
  }

  private static ValidatedObservationMessage copyWithInvalidKindAndSourceEventKey(
      ValidatedObservationMessage observation, String sourceEventKey) {
    return new ValidatedObservationMessage(
        observation.observationJson(),
        observation.eventId(),
        observation.machineId(),
        observation.componentId(),
        "INVALID_KIND",
        observation.sourceObservedAt(),
        observation.replaySessionId(),
        observation.replaySequence(),
        observation.replayPublishedAt(),
        sourceEventKey,
        observation.artifactId(),
        observation.rawRecordId(),
        observation.mappingVersion(),
        observation.sourceDataItemId());
  }
}
