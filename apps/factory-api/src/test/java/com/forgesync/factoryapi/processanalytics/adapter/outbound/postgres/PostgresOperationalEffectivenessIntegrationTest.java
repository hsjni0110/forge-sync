package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class PostgresOperationalEffectivenessIntegrationTest {
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private static DataSource dataSource;
  private static JdbcClient jdbcClient;

  @BeforeAll
  static void migrateDatabase() {
    dataSource =
        new DriverManagerDataSource(
            System.getenv()
                .getOrDefault(
                    "FORGESYNC_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15432/forgesync"),
            System.getenv().getOrDefault("FORGESYNC_DATABASE_USERNAME", "forgesync"),
            System.getenv().getOrDefault("FORGESYNC_DATABASE_PASSWORD", "forgesync-test"));
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
  }

  @BeforeEach
  void clearHistory() {
    jdbcClient
        .sql("TRUNCATE canonical_observation_history, ingestion_inbox RESTART IDENTITY CASCADE")
        .update();
  }

  @Test
  void readsPartCountAtTheRequestedCursorWithoutRepairingUnavailableValues() {
    store(1, "AVAILABLE", "10");
    store(2, "UNAVAILABLE", null);
    store(3, "AVAILABLE", "2");

    var observations =
        repository().readPartCounts("Mazak01", SESSION, 2, START, START.plusSeconds(10));

    assertThat(observations).hasSize(2);
    assertThat(observations.getFirst().value()).isEqualByComparingTo("10");
    assertThat(observations.getLast().isAvailable()).isFalse();
    assertThat(
            jdbcClient
                .sql("SELECT COUNT(*) FROM operational_effectiveness_processing")
                .query(Integer.class)
                .single())
        .isZero();
  }

  private static PostgresOperationalEffectivenessRepository repository() {
    return new PostgresOperationalEffectivenessRepository(
        jdbcClient,
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        new ObjectMapper().findAndRegisterModules());
  }

  private static void store(long sequence, String availability, String value) {
    String key = "part-" + sequence;
    Instant at = START.plusSeconds(sequence);
    UUID eventId = UUID.nameUUIDFromBytes(key.getBytes());
    String valueField = value == null ? "" : ",\"value\":" + value;
    String envelope =
        "{\"schemaVersion\":\"2.1.0\",\"observationKind\":\"EVENT\","
            + "\"payload\":{\"eventType\":\"PART_COUNT\",\"availability\":\""
            + availability
            + "\""
            + valueField
            + "}}";
    jdbcClient
        .sql(
            """
        INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
        VALUES (:session, :key, :event_id, :at)
        """)
        .param("session", SESSION)
        .param("key", key)
        .param("event_id", eventId)
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
        ) VALUES (:event_id, :session, :key, '2.1.0', 'Mazak01', 'Mazak01-path', 'EVENT',
          :at, :sequence, :at, :at, :artifact, :raw, '2.2.0', 'Mazak01-path_6',
          CAST(:envelope AS jsonb))
        """)
        .param("event_id", eventId)
        .param("session", SESSION)
        .param("key", key)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .param("sequence", sequence)
        .param("artifact", "sha256:" + "a".repeat(64))
        .param("raw", "raw-" + sequence)
        .param("envelope", envelope)
        .update();
  }
}
