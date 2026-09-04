package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureObservationHistory;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingResult;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProjectionStore;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureSet;
import com.forgesync.factoryapi.processanalytics.domain.CycleObservation;
import com.forgesync.factoryapi.processanalytics.domain.CycleSignal;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresCycleFeatureRepository
    implements CycleFeatureObservationHistory, CycleFeatureProjectionStore {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public PostgresCycleFeatureRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public List<CycleObservation> readCycleObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence, Instant beforeExclusive) {
    return jdbcClient
        .sql(
            """
            SELECT replay_session_id, replay_sequence, source_observed_at, source_event_key,
              canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine_id
              AND replay_session_id = :replay_session_id
              AND replay_sequence <= :through_replay_sequence
              AND source_observed_at < :before_exclusive
              AND (
                canonical_envelope #>> '{payload,eventType}' = 'EXECUTION'
                OR canonical_envelope #>> '{payload,metric}' IN (
                  'SPINDLE_SPEED', 'LOAD', 'PATH_FEEDRATE'
                )
              )
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .param("before_exclusive", OffsetDateTime.ofInstant(beforeExclusive, ZoneOffset.UTC))
        .query(
            (resultSet, rowNumber) ->
                toObservation(
                    resultSet.getObject("replay_session_id", UUID.class),
                    resultSet.getLong("replay_sequence"),
                    resultSet.getObject("source_observed_at", OffsetDateTime.class),
                    resultSet.getString("source_event_key"),
                    resultSet.getString("canonical_envelope")))
        .list();
  }

  @Override
  public boolean preserve(CycleFeatureProcessingResult result) {
    Boolean created =
        transactionTemplate.execute(
            status -> {
              if (insertProcessingRun(result) == 0) return false;
              result.featureSets().forEach(set -> insertFeatureSet(result, set));
              return true;
            });
    return Boolean.TRUE.equals(created);
  }

  @Override
  public Optional<CycleFeatureProcessingResult> findCycleFeatureProcessingRun(
      String featureProcessingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT feature_processing_run_id, machining_run_processing_run_id, machine_id,
              cycle_feature_version, input_hash, input_observation_count, eligible_run_count,
              result_hash, created_at
            FROM cycle_feature_processing_run
            WHERE feature_processing_run_id = :feature_processing_run_id
            """)
        .param("feature_processing_run_id", featureProcessingRunId)
        .query(
            (resultSet, rowNumber) ->
                new CycleFeatureProcessingResult(
                    resultSet.getString("feature_processing_run_id"),
                    resultSet.getString("machining_run_processing_run_id"),
                    resultSet.getString("machine_id"),
                    resultSet.getString("cycle_feature_version"),
                    resultSet.getString("input_hash"),
                    resultSet.getInt("input_observation_count"),
                    resultSet.getInt("eligible_run_count"),
                    resultSet.getString("result_hash"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                    false,
                    readFeatureSets(featureProcessingRunId)))
        .optional();
  }

  private int insertProcessingRun(CycleFeatureProcessingResult result) {
    return jdbcClient
        .sql(
            """
            INSERT INTO cycle_feature_processing_run (
              feature_processing_run_id, machining_run_processing_run_id, machine_id,
              cycle_feature_version, input_hash, input_observation_count, eligible_run_count,
              result_hash, created_at
            ) VALUES (
              :feature_processing_run_id, :machining_run_processing_run_id, :machine_id,
              :cycle_feature_version, :input_hash, :input_observation_count, :eligible_run_count,
              :result_hash, :created_at
            ) ON CONFLICT (feature_processing_run_id) DO NOTHING
            """)
        .param("feature_processing_run_id", result.featureProcessingRunId())
        .param("machining_run_processing_run_id", result.machiningRunProcessingRunId())
        .param("machine_id", result.machineId())
        .param("cycle_feature_version", result.cycleFeatureVersion())
        .param("input_hash", result.inputHash())
        .param("input_observation_count", result.inputObservationCount())
        .param("eligible_run_count", result.eligibleRunCount())
        .param("result_hash", result.resultHash())
        .param("created_at", OffsetDateTime.ofInstant(result.createdAt(), ZoneOffset.UTC))
        .update();
  }

  private void insertFeatureSet(CycleFeatureProcessingResult result, CycleFeatureSet set) {
    var feature = set.cycleFeature();
    jdbcClient
        .sql(
            """
            INSERT INTO cycle_feature_projection (
              feature_processing_run_id, cycle_feature_set_id, machining_run_id, machine_id,
              started_at, ended_at, feature_status, result_hash, feature_projection
            ) VALUES (
              :feature_processing_run_id, :cycle_feature_set_id, :machining_run_id, :machine_id,
              :started_at, :ended_at, :feature_status, :result_hash,
              CAST(:feature_projection AS JSONB)
            )
            """)
        .param("feature_processing_run_id", result.featureProcessingRunId())
        .param("cycle_feature_set_id", set.cycleFeatureSetId())
        .param("machining_run_id", feature.machiningRunId())
        .param("machine_id", result.machineId())
        .param("started_at", OffsetDateTime.ofInstant(feature.startedAt(), ZoneOffset.UTC))
        .param("ended_at", OffsetDateTime.ofInstant(feature.endedAt(), ZoneOffset.UTC))
        .param("feature_status", feature.status().name())
        .param("result_hash", feature.resultHash())
        .param("feature_projection", write(CycleFeatureDocument.from(set)))
        .update();
  }

  private List<CycleFeatureSet> readFeatureSets(String featureProcessingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT feature_projection FROM cycle_feature_projection
            WHERE feature_processing_run_id = :feature_processing_run_id
            ORDER BY started_at, cycle_feature_set_id
            """)
        .param("feature_processing_run_id", featureProcessingRunId)
        .query(String.class)
        .list()
        .stream()
        .map(this::read)
        .map(CycleFeatureDocument::toDomain)
        .toList();
  }

  private CycleObservation toObservation(
      UUID replaySessionId,
      long replaySequence,
      OffsetDateTime sourceObservedAt,
      String sourceEventKey,
      String envelope) {
    try {
      JsonNode root = objectMapper.readTree(envelope);
      JsonNode payload = root.path("payload");
      String signalName = payload.path("eventType").asText(null);
      if (signalName == null) signalName = payload.path("metric").asText();
      CycleSignal signal = CycleSignal.valueOf(signalName);
      boolean available = "AVAILABLE".equals(payload.path("availability").asText());
      JsonNode transformation = root.path("provenance").path("transformation");
      JsonNode source = root.path("provenance").path("source");
      String sourceDataItemId = transformation.path("sourceDataItemId").asText();
      return new CycleObservation(
          root.path("machineId").asText(),
          replaySessionId,
          replaySequence,
          sourceObservedAt.toInstant(),
          sourceEventKey,
          signal,
          root.path("subject").path("componentId").asText(),
          sourceDataItemId,
          payload.path("unit").asText(null),
          available,
          available && !signal.isMetric() ? payload.path("value").asText() : null,
          available && signal.isMetric() ? payload.path("value").decimalValue() : null,
          new ObservationProvenance(
              source.path("kind").asText(),
              source.path("provider").asText(),
              source.path("sourceSetId").asText(),
              source.path("artifactId").asText(),
              transformation.path("rawRecordId").asText(),
              transformation.path("mappingVersion").asText(),
              sourceDataItemId));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be decoded", exception);
    }
  }

  private String write(CycleFeatureDocument document) {
    try {
      return objectMapper.writeValueAsString(document);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cycle Feature projection cannot be encoded", exception);
    }
  }

  private CycleFeatureDocument read(String document) {
    try {
      return objectMapper.readValue(document, CycleFeatureDocument.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Stored Cycle Feature projection cannot be decoded", exception);
    }
  }
}
