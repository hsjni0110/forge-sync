package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.forgesync.factoryapi.application.IngestionResult;
import com.forgesync.factoryapi.application.ObservationTransaction;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresObservationTransaction implements ObservationTransaction {

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;

  public PostgresObservationTransaction(
      JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Override
  public IngestionResult storeObservation(
      ValidatedObservationMessage observation, Instant ingestedAt) {
    IngestionResult result =
        transactionTemplate.execute(status -> storeWithinTransaction(observation, ingestedAt));
    return Objects.requireNonNull(result, "transaction result");
  }

  private IngestionResult storeWithinTransaction(
      ValidatedObservationMessage observation, Instant ingestedAt) {
    int claimed = insertInbox(observation, ingestedAt);
    if (claimed == 0) {
      return IngestionResult.SKIPPED_DUPLICATE;
    }
    insertObservation(observation, ingestedAt);
    return IngestionResult.ACCEPTED;
  }

  private int insertInbox(ValidatedObservationMessage observation, Instant ingestedAt) {
    return jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (
              replay_session_id, source_event_key, event_id, ingested_at
            ) VALUES (
              :replay_session_id, :source_event_key, :event_id, :ingested_at
            )
            ON CONFLICT (replay_session_id, source_event_key) DO NOTHING
            """)
        .param("replay_session_id", observation.replaySessionId())
        .param("source_event_key", observation.sourceEventKey())
        .param("event_id", observation.eventId())
        .param("ingested_at", asUtcOffset(ingestedAt))
        .update();
  }

  private void insertObservation(ValidatedObservationMessage observation, Instant ingestedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO canonical_observation_history (
              event_id, replay_session_id, source_event_key, schema_version, machine_id,
              component_id, observation_kind, source_observed_at, replay_sequence,
              replay_published_at, ingested_at, artifact_id, raw_record_id, mapping_version,
              source_data_item_id, canonical_envelope
            ) VALUES (
              :event_id, :replay_session_id, :source_event_key, '2.0.0', :machine_id,
              :component_id, :observation_kind, :source_observed_at, :replay_sequence,
              :replay_published_at, :ingested_at, :artifact_id, :raw_record_id, :mapping_version,
              :source_data_item_id, CAST(:canonical_envelope AS jsonb)
            )
            """)
        .param("event_id", observation.eventId())
        .param("replay_session_id", observation.replaySessionId())
        .param("source_event_key", observation.sourceEventKey())
        .param("machine_id", observation.machineId())
        .param("component_id", observation.componentId())
        .param("observation_kind", observation.observationKind())
        .param("source_observed_at", asUtcOffset(observation.sourceObservedAt()))
        .param("replay_sequence", observation.replaySequence())
        .param("replay_published_at", asUtcOffset(observation.replayPublishedAt()))
        .param("ingested_at", asUtcOffset(ingestedAt))
        .param("artifact_id", observation.artifactId())
        .param("raw_record_id", observation.rawRecordId())
        .param("mapping_version", observation.mappingVersion())
        .param("source_data_item_id", observation.sourceDataItemId())
        .param("canonical_envelope", observation.observationJson())
        .update();
  }

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
