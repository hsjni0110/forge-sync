package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.forgesync.factoryapi.application.IngestionResult;
import com.forgesync.factoryapi.application.ObservationTransaction;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionNotifier;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationOrder;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationOrderingPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.ProjectionDecision;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresObservationTransaction implements ObservationTransaction {

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;
  private final ObservationOrderingPolicy observationOrderingPolicy;
  private final PostgresEquipmentStateProjection equipmentStateProjection;
  private final TwinProjectionNotifier twinProjectionNotifier;

  public PostgresObservationTransaction(
      JdbcClient jdbcClient,
      PlatformTransactionManager transactionManager,
      ObservationOrderingPolicy observationOrderingPolicy,
      PostgresEquipmentStateProjection equipmentStateProjection) {
    this(
        jdbcClient,
        transactionManager,
        observationOrderingPolicy,
        equipmentStateProjection,
        TwinProjectionNotifier.noOp());
  }

  public PostgresObservationTransaction(
      JdbcClient jdbcClient,
      PlatformTransactionManager transactionManager,
      ObservationOrderingPolicy observationOrderingPolicy,
      PostgresEquipmentStateProjection equipmentStateProjection,
      TwinProjectionNotifier twinProjectionNotifier) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.observationOrderingPolicy = Objects.requireNonNull(observationOrderingPolicy);
    this.equipmentStateProjection = Objects.requireNonNull(equipmentStateProjection);
    this.twinProjectionNotifier = Objects.requireNonNull(twinProjectionNotifier);
  }

  @Override
  public IngestionResult storeObservation(
      ValidatedObservationMessage observation, Instant ingestedAt, Instant projectedAt) {
    IngestionResult result =
        transactionTemplate.execute(
            status -> storeWithinTransaction(observation, ingestedAt, projectedAt));
    IngestionResult committedResult = Objects.requireNonNull(result, "transaction result");
    if (committedResult == IngestionResult.ACCEPTED) {
      twinProjectionNotifier.projectionCommitted(observation.machineId());
    }
    return committedResult;
  }

  private IngestionResult storeWithinTransaction(
      ValidatedObservationMessage observation, Instant ingestedAt, Instant projectedAt) {
    int claimed = insertInbox(observation, ingestedAt);
    if (claimed == 0) {
      return IngestionResult.SKIPPED_DUPLICATE;
    }
    insertObservation(observation, ingestedAt);
    if (!isActiveReplaySession(observation)) {
      return IngestionResult.ACCEPTED_LATE;
    }
    return projectLatestObservation(observation, projectedAt);
  }

  private boolean isActiveReplaySession(ValidatedObservationMessage observation) {
    return jdbcClient
        .sql(
            """
            SELECT NOT EXISTS (
              SELECT 1 FROM active_replay_projection WHERE machine_id = :machine_id
            ) OR EXISTS (
              SELECT 1 FROM active_replay_projection
              WHERE machine_id = :machine_id AND replay_session_id = :replay_session_id
            )
            """)
        .param("machine_id", observation.machineId())
        .param("replay_session_id", observation.replaySessionId())
        .query(Boolean.class)
        .single();
  }

  private IngestionResult projectLatestObservation(
      ValidatedObservationMessage observation, Instant projectedAt) {
    TwinVersion currentVersion = lockMachineVersion(observation.machineId());
    Optional<ObservationOrder> currentObservation = findCurrentObservation(observation);
    if (currentObservation.isPresent()
        && observationOrderingPolicy.decide(orderOf(observation), currentObservation.get())
            == ProjectionDecision.KEEP_CURRENT) {
      return IngestionResult.ACCEPTED_LATE;
    }

    TwinVersion nextVersion = currentVersion.next();
    updateMachineVersion(observation, nextVersion, projectedAt);
    upsertLatestObservation(observation, nextVersion, projectedAt);
    equipmentStateProjection.project(observation.machineId(), nextVersion, projectedAt);
    return IngestionResult.ACCEPTED;
  }

  private TwinVersion lockMachineVersion(String machineId) {
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_twin_version (machine_id, twin_version, projected_at)
            VALUES (:machine_id, 0, NULL)
            ON CONFLICT (machine_id) DO NOTHING
            """)
        .param("machine_id", machineId)
        .update();
    long version =
        jdbcClient
            .sql(
                """
                SELECT twin_version FROM equipment_twin_version
                WHERE machine_id = :machine_id
                FOR UPDATE
                """)
            .param("machine_id", machineId)
            .query(Long.class)
            .single();
    return new TwinVersion(version);
  }

  private Optional<ObservationOrder> findCurrentObservation(
      ValidatedObservationMessage observation) {
    return jdbcClient
        .sql(
            """
            SELECT replay_session_id, replay_sequence, source_observed_at, source_event_key
            FROM latest_observation_projection
            WHERE machine_id = :machine_id AND source_data_item_id = :source_data_item_id
            """)
        .param("machine_id", observation.machineId())
        .param("source_data_item_id", observation.sourceDataItemId())
        .query(
            (resultSet, rowNumber) ->
                new ObservationOrder(
                    resultSet.getObject("replay_session_id", UUID.class),
                    resultSet.getLong("replay_sequence"),
                    resultSet.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                    resultSet.getString("source_event_key")))
        .optional();
  }

  private void updateMachineVersion(
      ValidatedObservationMessage observation, TwinVersion twinVersion, Instant projectedAt) {
    // A new value for one DataItem can arrive behind another DataItem's cursor. Keep the
    // machine watermark while still versioning that field update in this transaction.
    jdbcClient
        .sql(
            """
            WITH cursor_order AS (
              SELECT replay_session_id = :replay_session_id
                  AND replay_sequence > :replay_sequence AS keep_cursor
              FROM equipment_twin_version WHERE machine_id = :machine_id
            )
            UPDATE equipment_twin_version version
            SET twin_version = :twin_version,
                projected_at = :projected_at,
                replay_session_id = CASE WHEN cursor_order.keep_cursor
                  THEN version.replay_session_id ELSE :replay_session_id END,
                replay_sequence = CASE WHEN cursor_order.keep_cursor
                  THEN version.replay_sequence ELSE :replay_sequence END,
                source_observed_at = CASE WHEN cursor_order.keep_cursor
                  THEN version.source_observed_at ELSE :source_observed_at END,
                replay_published_at = CASE WHEN cursor_order.keep_cursor
                  THEN version.replay_published_at ELSE :replay_published_at END
            FROM cursor_order
            WHERE version.machine_id = :machine_id
            """)
        .param("twin_version", twinVersion.value())
        .param("projected_at", asUtcOffset(projectedAt))
        .param("replay_session_id", observation.replaySessionId())
        .param("replay_sequence", observation.replaySequence())
        .param("source_observed_at", asUtcOffset(observation.sourceObservedAt()))
        .param("replay_published_at", asUtcOffset(observation.replayPublishedAt()))
        .param("machine_id", observation.machineId())
        .update();
  }

  private void upsertLatestObservation(
      ValidatedObservationMessage observation, TwinVersion twinVersion, Instant projectedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO latest_observation_projection (
              machine_id, source_data_item_id, event_id, component_id, observation_kind,
              replay_session_id, replay_sequence, source_observed_at, source_event_key,
              projected_at, twin_version, artifact_id, raw_record_id, mapping_version,
              canonical_envelope
            ) VALUES (
              :machine_id, :source_data_item_id, :event_id, :component_id, :observation_kind,
              :replay_session_id, :replay_sequence, :source_observed_at, :source_event_key,
              :projected_at, :twin_version, :artifact_id, :raw_record_id, :mapping_version,
              CAST(:canonical_envelope AS jsonb)
            )
            ON CONFLICT (machine_id, source_data_item_id) DO UPDATE SET
              event_id = EXCLUDED.event_id,
              component_id = EXCLUDED.component_id,
              observation_kind = EXCLUDED.observation_kind,
              replay_session_id = EXCLUDED.replay_session_id,
              replay_sequence = EXCLUDED.replay_sequence,
              source_observed_at = EXCLUDED.source_observed_at,
              source_event_key = EXCLUDED.source_event_key,
              projected_at = EXCLUDED.projected_at,
              twin_version = EXCLUDED.twin_version,
              artifact_id = EXCLUDED.artifact_id,
              raw_record_id = EXCLUDED.raw_record_id,
              mapping_version = EXCLUDED.mapping_version,
              canonical_envelope = EXCLUDED.canonical_envelope
            """)
        .param("machine_id", observation.machineId())
        .param("source_data_item_id", observation.sourceDataItemId())
        .param("event_id", observation.eventId())
        .param("component_id", observation.componentId())
        .param("observation_kind", observation.observationKind())
        .param("replay_session_id", observation.replaySessionId())
        .param("replay_sequence", observation.replaySequence())
        .param("source_observed_at", asUtcOffset(observation.sourceObservedAt()))
        .param("source_event_key", observation.sourceEventKey())
        .param("projected_at", asUtcOffset(projectedAt))
        .param("twin_version", twinVersion.value())
        .param("artifact_id", observation.artifactId())
        .param("raw_record_id", observation.rawRecordId())
        .param("mapping_version", observation.mappingVersion())
        .param("canonical_envelope", observation.observationJson())
        .update();
  }

  private static ObservationOrder orderOf(ValidatedObservationMessage observation) {
    return new ObservationOrder(
        observation.replaySessionId(),
        observation.replaySequence(),
        observation.sourceObservedAt(),
        observation.sourceEventKey());
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
              :event_id, :replay_session_id, :source_event_key, :schema_version, :machine_id,
              :component_id, :observation_kind, :source_observed_at, :replay_sequence,
              :replay_published_at, :ingested_at, :artifact_id, :raw_record_id, :mapping_version,
              :source_data_item_id, CAST(:canonical_envelope AS jsonb)
            )
            """)
        .param("event_id", observation.eventId())
        .param("replay_session_id", observation.replaySessionId())
        .param("source_event_key", observation.sourceEventKey())
        .param("schema_version", observation.schemaVersion())
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
