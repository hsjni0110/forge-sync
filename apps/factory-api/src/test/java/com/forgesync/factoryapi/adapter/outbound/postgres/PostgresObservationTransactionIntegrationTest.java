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
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationOrderingPolicy;
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
  private static final Instant PROJECTED_AT = Instant.parse("2026-09-02T01:02:04Z");
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
            jdbcClient,
            new DataSourceTransactionManager(dataSource),
            new ObservationOrderingPolicy());
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient
        .sql(
            """
            TRUNCATE latest_observation_projection, equipment_twin_version,
              canonical_observation_history, ingestion_inbox
            """)
        .update();
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
                  return transaction.storeObservation(observation, INGESTED_AT, PROJECTED_AT);
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
    assertThat(transaction.storeObservation(accepted, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    String conflictingSourceEventKey = accepted.sourceEventKey() + "#conflict";
    ValidatedObservationMessage conflicting =
        copyWithInvalidKindAndSourceEventKey(accepted, conflictingSourceEventKey);

    assertThatThrownBy(() -> transaction.storeObservation(conflicting, INGESTED_AT, PROJECTED_AT))
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

    assertThat(transaction.storeObservation(firstReplay, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(secondReplay, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED_LATE);

    assertThat(firstReplay.eventId()).isEqualTo(secondReplay.eventId());
    assertThat(rowCount("ingestion_inbox")).isEqualTo(2);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(2);
  }

  @Test
  void preservesCanonicalCategoryTimesAndProvenanceForEveryVariant() {
    ValidatedObservationMessage sample = replayedObservation("sample-spindle-speed.json", 1);
    ValidatedObservationMessage event = replayedObservation("event-execution.json", 2);
    ValidatedObservationMessage condition = replayedObservation("condition-warning.json", 3);

    assertThat(transaction.storeObservation(sample, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(event, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(condition, INGESTED_AT, PROJECTED_AT))
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
    assertThat(rowCount("latest_observation_projection")).isEqualTo(3);
    assertThat(twinVersion("Mazak01")).isEqualTo(3);
  }

  @Test
  void storesLateHistoryWithoutRollingBackLatestProjection() {
    ValidatedObservationMessage current =
        replayedEvent(42, REPLAY_SESSION_ID, Instant.parse("2016-10-05T09:01:37Z"), "current");
    ValidatedObservationMessage late =
        replayedEvent(41, REPLAY_SESSION_ID, Instant.parse("2016-10-06T09:01:37Z"), "late");

    assertThat(transaction.storeObservation(current, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(
            transaction.storeObservation(
                late, INGESTED_AT.plusSeconds(1), PROJECTED_AT.plusSeconds(1)))
        .isEqualTo(IngestionResult.ACCEPTED_LATE);

    assertThat(rowCount("canonical_observation_history")).isEqualTo(2);
    assertThat(latestReplaySequence(current)).isEqualTo(42);
    assertThat(latestEventId(current)).isEqualTo(current.eventId());
    assertThat(latestProjectedAt(current)).isEqualTo(PROJECTED_AT);
    assertThat(twinVersion(current.machineId())).isEqualTo(1);
  }

  @Test
  void usesHistoricalSourceTimeAcrossReplaySessions() {
    UUID anotherReplaySession = UUID.fromString("5dfd98c9-532e-43a7-ac9f-31b2db874920");
    ValidatedObservationMessage current =
        replayedEvent(1, REPLAY_SESSION_ID, Instant.parse("2016-10-05T10:00:00Z"), "current");
    ValidatedObservationMessage late =
        replayedEvent(999, anotherReplaySession, Instant.parse("2016-10-05T09:00:00Z"), "late");

    assertThat(transaction.storeObservation(current, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(
            transaction.storeObservation(
                late, INGESTED_AT.plusSeconds(1), PROJECTED_AT.plusSeconds(1)))
        .isEqualTo(IngestionResult.ACCEPTED_LATE);

    assertThat(latestEventId(current)).isEqualTo(current.eventId());
    assertThat(twinVersion(current.machineId())).isEqualTo(1);
  }

  @Test
  void concurrentOutOfOrderDeliveriesConvergeOnNewerObservation() throws Exception {
    ValidatedObservationMessage older =
        replayedEvent(41, REPLAY_SESSION_ID, Instant.parse("2016-10-05T09:00:00Z"), "older");
    ValidatedObservationMessage newer =
        replayedEvent(42, REPLAY_SESSION_ID, Instant.parse("2016-10-05T09:00:01Z"), "newer");
    CountDownLatch start = new CountDownLatch(1);

    List<IngestionResult> results = new ArrayList<>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<IngestionResult> olderResult = executor.submit(() -> storeAfterSignal(older, start));
      Future<IngestionResult> newerResult = executor.submit(() -> storeAfterSignal(newer, start));
      start.countDown();
      results.add(olderResult.get());
      results.add(newerResult.get());
    }

    assertThat(results).contains(IngestionResult.ACCEPTED);
    assertThat(results)
        .allMatch(
            result ->
                result == IngestionResult.ACCEPTED || result == IngestionResult.ACCEPTED_LATE);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(2);
    assertThat(latestEventId(newer)).isEqualTo(newer.eventId());
    assertThat(twinVersion(newer.machineId())).isBetween(1L, 2L);
  }

  @Test
  void projectionFailureRollsBackInboxAndHistory() {
    ValidatedObservationMessage observation = replayedObservation("event-execution.json", 42);
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_twin_version (machine_id, twin_version, projected_at)
            VALUES (:machine_id, :twin_version, :projected_at)
            """)
        .param("machine_id", observation.machineId())
        .param("twin_version", Long.MAX_VALUE)
        .param("projected_at", asUtcOffset(PROJECTED_AT))
        .update();

    assertThatThrownBy(() -> transaction.storeObservation(observation, INGESTED_AT, PROJECTED_AT))
        .isInstanceOf(ArithmeticException.class);

    assertThat(rowCount("ingestion_inbox")).isZero();
    assertThat(rowCount("canonical_observation_history")).isZero();
    assertThat(rowCount("latest_observation_projection")).isZero();
    assertThat(twinVersion(observation.machineId())).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void keepsIndependentVersionsForDifferentMachines() {
    ValidatedObservationMessage firstMachine =
        replayedEvent(1, REPLAY_SESSION_ID, Instant.parse("2016-10-05T09:00:00Z"), "first");
    ValidatedObservationMessage secondMachine =
        replayedEventForMachine("Mazak02", 1, Instant.parse("2016-10-05T09:00:00Z"), "second");

    assertThat(transaction.storeObservation(firstMachine, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(secondMachine, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);

    assertThat(twinVersion("Mazak01")).isEqualTo(1);
    assertThat(twinVersion("Mazak02")).isEqualTo(1);
  }

  @Test
  void keepsSameMetricSeparateBySourceDataItemIdentity() {
    ValidatedObservationMessage firstSpindle =
        replayedSampleForDataItem("Mazak01-C_5", "Mazak01-C", 1, "first-spindle");
    ValidatedObservationMessage secondSpindle =
        replayedSampleForDataItem("Mazak01-C2_5", "Mazak01-C2", 2, "second-spindle");

    assertThat(transaction.storeObservation(firstSpindle, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);
    assertThat(transaction.storeObservation(secondSpindle, INGESTED_AT, PROJECTED_AT))
        .isEqualTo(IngestionResult.ACCEPTED);

    assertThat(rowCount("latest_observation_projection")).isEqualTo(2);
    assertThat(twinVersion("Mazak01")).isEqualTo(2);
  }

  private static long rowCount(String tableName) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName).query(Long.class).single();
  }

  private static long twinVersion(String machineId) {
    return jdbcClient
        .sql(
            """
            SELECT twin_version FROM equipment_twin_version WHERE machine_id = :machine_id
            """)
        .param("machine_id", machineId)
        .query(Long.class)
        .single();
  }

  private static long latestReplaySequence(ValidatedObservationMessage observation) {
    return latestValue(observation, "replay_sequence", Long.class);
  }

  private static UUID latestEventId(ValidatedObservationMessage observation) {
    return latestValue(observation, "event_id", UUID.class);
  }

  private static Instant latestProjectedAt(ValidatedObservationMessage observation) {
    return latestValue(observation, "projected_at", OffsetDateTime.class).toInstant();
  }

  private static <T> T latestValue(
      ValidatedObservationMessage observation, String columnName, Class<T> valueType) {
    return jdbcClient
        .sql(
            "SELECT "
                + columnName
                + " FROM latest_observation_projection"
                + " WHERE machine_id = :machine_id AND source_data_item_id = :source_data_item_id")
        .param("machine_id", observation.machineId())
        .param("source_data_item_id", observation.sourceDataItemId())
        .query(valueType)
        .single();
  }

  private static IngestionResult storeAfterSignal(
      ValidatedObservationMessage observation, CountDownLatch start) throws InterruptedException {
    start.await();
    return transaction.storeObservation(observation, INGESTED_AT, PROJECTED_AT);
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
    return validateReplayedDocument(document, replaySequence, replaySessionId);
  }

  private static ValidatedObservationMessage replayedEvent(
      long replaySequence, UUID replaySessionId, Instant sourceObservedAt, String identitySuffix) {
    ObjectNode document = (ObjectNode) readFixture("event-execution.json");
    String sourceEventKey = document.path("sourceEventKey").asText() + "#" + identitySuffix;
    document.put(
        "eventId",
        UUID.nameUUIDFromBytes(sourceEventKey.getBytes(StandardCharsets.UTF_8)).toString());
    document.put("sourceEventKey", sourceEventKey);
    ((ObjectNode) document.path("source")).put("sourceObservedAt", sourceObservedAt.toString());
    return validateReplayedDocument(document, replaySequence, replaySessionId);
  }

  private static ValidatedObservationMessage replayedEventForMachine(
      String machineId, long replaySequence, Instant sourceObservedAt, String identitySuffix) {
    ObjectNode document = (ObjectNode) readFixture("event-execution.json");
    String sourceEventKey = document.path("sourceEventKey").asText() + "#" + identitySuffix;
    document.put(
        "eventId",
        UUID.nameUUIDFromBytes(sourceEventKey.getBytes(StandardCharsets.UTF_8)).toString());
    document.put("sourceEventKey", sourceEventKey);
    document.put("machineId", machineId);
    ((ObjectNode) document.path("source")).put("sourceObservedAt", sourceObservedAt.toString());
    return validateReplayedDocument(document, replaySequence, REPLAY_SESSION_ID);
  }

  private static ValidatedObservationMessage replayedSampleForDataItem(
      String sourceDataItemId, String componentId, long replaySequence, String identitySuffix) {
    ObjectNode document = (ObjectNode) readFixture("sample-spindle-speed.json");
    String sourceEventKey = document.path("sourceEventKey").asText() + "#" + identitySuffix;
    document.put(
        "eventId",
        UUID.nameUUIDFromBytes(sourceEventKey.getBytes(StandardCharsets.UTF_8)).toString());
    document.put("sourceEventKey", sourceEventKey);
    ((ObjectNode) document.path("subject")).put("componentId", componentId);
    ((ObjectNode) document.path("provenance").path("transformation"))
        .put("sourceDataItemId", sourceDataItemId);
    return validateReplayedDocument(document, replaySequence, REPLAY_SESSION_ID);
  }

  private static ValidatedObservationMessage validateReplayedDocument(
      ObjectNode document, long replaySequence, UUID replaySessionId) {
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

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
