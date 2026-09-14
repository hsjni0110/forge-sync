package com.forgesync.factoryapi.dataquality.adapter.outbound.postgres;

import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import com.forgesync.factoryapi.dataquality.application.DataQualityObservationParticipant;
import com.forgesync.factoryapi.dataquality.application.RuntimeObservationOutcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresRuntimeDataQualityProjection
    implements DataQualityObservationParticipant {
  private final JdbcClient jdbcClient;

  public PostgresRuntimeDataQualityProjection(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
  }

  @Override
  public void record(
      ValidatedObservationMessage observation,
      Instant ingestedAt,
      RuntimeObservationOutcome outcome) {
    boolean accepted = outcome == RuntimeObservationOutcome.ACCEPTED;
    jdbcClient
        .sql(
            """
            INSERT INTO runtime_data_quality_projection (
              machine_id, replay_session_id, received_count, accepted_count, duplicate_count,
              out_of_order_count, max_replay_sequence, max_source_observed_at,
              max_source_event_key, first_ingested_at, last_ingested_at
            ) VALUES (
              :machine_id, :replay_session_id, 1, :accepted, :duplicate, 0,
              :replay_sequence, :source_observed_at, :source_event_key, :ingested_at, :ingested_at
            )
            ON CONFLICT (machine_id, replay_session_id) DO UPDATE SET
              received_count = runtime_data_quality_projection.received_count + 1,
              accepted_count = runtime_data_quality_projection.accepted_count + :accepted,
              duplicate_count = runtime_data_quality_projection.duplicate_count + :duplicate,
              out_of_order_count = runtime_data_quality_projection.out_of_order_count +
                CASE WHEN :accepted = 1 AND
                  ROW(:replay_sequence, :source_observed_at, :source_event_key) <
                  ROW(runtime_data_quality_projection.max_replay_sequence,
                      runtime_data_quality_projection.max_source_observed_at,
                      runtime_data_quality_projection.max_source_event_key)
                THEN 1 ELSE 0 END,
              max_replay_sequence = CASE WHEN
                ROW(:replay_sequence, :source_observed_at, :source_event_key) >
                ROW(runtime_data_quality_projection.max_replay_sequence,
                    runtime_data_quality_projection.max_source_observed_at,
                    runtime_data_quality_projection.max_source_event_key)
                THEN :replay_sequence ELSE runtime_data_quality_projection.max_replay_sequence END,
              max_source_observed_at = CASE WHEN
                ROW(:replay_sequence, :source_observed_at, :source_event_key) >
                ROW(runtime_data_quality_projection.max_replay_sequence,
                    runtime_data_quality_projection.max_source_observed_at,
                    runtime_data_quality_projection.max_source_event_key)
                THEN :source_observed_at ELSE runtime_data_quality_projection.max_source_observed_at END,
              max_source_event_key = CASE WHEN
                ROW(:replay_sequence, :source_observed_at, :source_event_key) >
                ROW(runtime_data_quality_projection.max_replay_sequence,
                    runtime_data_quality_projection.max_source_observed_at,
                    runtime_data_quality_projection.max_source_event_key)
                THEN :source_event_key ELSE runtime_data_quality_projection.max_source_event_key END,
              last_ingested_at = GREATEST(runtime_data_quality_projection.last_ingested_at, :ingested_at)
            """)
        .param("machine_id", observation.machineId())
        .param("replay_session_id", observation.replaySessionId())
        .param("accepted", accepted ? 1 : 0)
        .param("duplicate", accepted ? 0 : 1)
        .param("replay_sequence", observation.replaySequence())
        .param(
            "source_observed_at",
            OffsetDateTime.ofInstant(observation.sourceObservedAt(), ZoneOffset.UTC))
        .param("source_event_key", observation.sourceEventKey())
        .param("ingested_at", OffsetDateTime.ofInstant(ingestedAt, ZoneOffset.UTC))
        .update();
    recordCursorScopedObservation(observation, ingestedAt, accepted);
  }

  private void recordCursorScopedObservation(
      ValidatedObservationMessage observation, Instant ingestedAt, boolean accepted) {
    jdbcClient
        .sql(
            """
            INSERT INTO runtime_data_quality_observation (
              machine_id, replay_session_id, source_event_key, replay_sequence,
              received_count, accepted_count, duplicate_count, out_of_order_count,
              first_ingested_at, last_ingested_at
            )
            SELECT :machine_id, :replay_session_id, :source_event_key, :replay_sequence,
              1, :accepted, :duplicate,
              CASE WHEN :accepted = 1 AND
                ROW(:replay_sequence, :source_observed_at, :source_event_key) <
                ROW(max_replay_sequence, max_source_observed_at, max_source_event_key)
              THEN 1 ELSE 0 END,
              :ingested_at, :ingested_at
            FROM runtime_data_quality_projection
            WHERE machine_id = :machine_id AND replay_session_id = :replay_session_id
            ON CONFLICT (machine_id, replay_session_id, source_event_key) DO UPDATE SET
              received_count = runtime_data_quality_observation.received_count + 1,
              duplicate_count = runtime_data_quality_observation.duplicate_count + 1,
              last_ingested_at = GREATEST(
                runtime_data_quality_observation.last_ingested_at, :ingested_at)
            """)
        .param("machine_id", observation.machineId())
        .param("replay_session_id", observation.replaySessionId())
        .param("source_event_key", observation.sourceEventKey())
        .param("replay_sequence", observation.replaySequence())
        .param("accepted", accepted ? 1 : 0)
        .param("duplicate", accepted ? 0 : 1)
        .param(
            "source_observed_at",
            OffsetDateTime.ofInstant(observation.sourceObservedAt(), ZoneOffset.UTC))
        .param("ingested_at", OffsetDateTime.ofInstant(ingestedAt, ZoneOffset.UTC))
        .update();
  }
}
