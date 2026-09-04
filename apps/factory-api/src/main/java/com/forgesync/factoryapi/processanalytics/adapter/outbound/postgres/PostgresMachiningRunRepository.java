package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.application.CanonicalObservationHistory;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingResult;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProjectionStore;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ProcessObservation;
import com.forgesync.factoryapi.processanalytics.domain.ProcessSignal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresMachiningRunRepository
    implements CanonicalObservationHistory, MachiningRunProjectionStore {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public PostgresMachiningRunRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public List<ProcessObservation> readRelevantObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
            SELECT replay_session_id, replay_sequence, source_observed_at, source_event_key,
              canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine_id
              AND replay_session_id = :replay_session_id
              AND replay_sequence <= :through_replay_sequence
              AND (
                canonical_envelope #>> '{payload,eventType}' IN ('EXECUTION', 'PROGRAM')
                OR canonical_envelope #>> '{payload,metric}' = 'SPINDLE_SPEED'
              )
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
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
  public boolean preserve(MachiningRunProcessingResult processingResult) {
    Boolean created =
        transactionTemplate.execute(
            status -> {
              int inserted = insertProcessingRun(processingResult);
              if (inserted == 0) {
                return false;
              }
              for (MachiningRun run : processingResult.machiningRuns()) {
                insertMachiningRun(run);
              }
              return true;
            });
    return Boolean.TRUE.equals(created);
  }

  @Override
  public Optional<MachiningRunProcessingResult> findProcessingRun(String processingRunId) {
    Optional<MachiningRunProcessingResult> metadata =
        jdbcClient
            .sql(
                """
            SELECT processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              segmentation_rule_version, input_hash, input_observation_count, result_hash,
              created_at
            FROM process_analytics_processing_run
            WHERE processing_run_id = :processing_run_id
            """)
            .param("processing_run_id", processingRunId)
            .query(
                (resultSet, rowNumber) ->
                    new MachiningRunProcessingResult(
                        resultSet.getString("processing_run_id"),
                        resultSet.getString("machine_id"),
                        resultSet.getObject("replay_session_id", UUID.class),
                        resultSet.getLong("through_replay_sequence"),
                        resultSet.getString("segmentation_rule_version"),
                        resultSet.getString("input_hash"),
                        resultSet.getInt("input_observation_count"),
                        resultSet.getString("result_hash"),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                        false,
                        List.of()))
            .optional();
    return metadata.map(
        result ->
            new MachiningRunProcessingResult(
                result.processingRunId(),
                result.machineId(),
                result.replaySessionId(),
                result.throughReplaySequence(),
                result.segmentationRuleVersion(),
                result.inputHash(),
                result.inputObservationCount(),
                result.resultHash(),
                result.createdAt(),
                false,
                findMachiningRuns(result.machineId(), result.processingRunId())));
  }

  @Override
  public List<MachiningRun> findMachiningRuns(String machineId, String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT run_projection
            FROM machining_run_projection
            WHERE machine_id = :machine_id AND processing_run_id = :processing_run_id
            ORDER BY started_at, machining_run_id
            """)
        .param("machine_id", machineId)
        .param("processing_run_id", processingRunId)
        .query(String.class)
        .list()
        .stream()
        .map(this::readDocument)
        .map(MachiningRunDocument::toDomain)
        .toList();
  }

  private int insertProcessingRun(MachiningRunProcessingResult result) {
    return jdbcClient
        .sql(
            """
            INSERT INTO process_analytics_processing_run (
              processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              segmentation_rule_version, input_hash, input_observation_count, result_hash,
              created_at
            ) VALUES (
              :processing_run_id, :machine_id, :replay_session_id, :through_replay_sequence,
              :segmentation_rule_version, :input_hash, :input_observation_count, :result_hash,
              :created_at
            )
            ON CONFLICT (processing_run_id) DO NOTHING
            """)
        .param("processing_run_id", result.processingRunId())
        .param("machine_id", result.machineId())
        .param("replay_session_id", result.replaySessionId())
        .param("through_replay_sequence", result.throughReplaySequence())
        .param("segmentation_rule_version", result.segmentationRuleVersion())
        .param("input_hash", result.inputHash())
        .param("input_observation_count", result.inputObservationCount())
        .param("result_hash", result.resultHash())
        .param("created_at", OffsetDateTime.ofInstant(result.createdAt(), ZoneOffset.UTC))
        .update();
  }

  private void insertMachiningRun(MachiningRun run) {
    jdbcClient
        .sql(
            """
            INSERT INTO machining_run_projection (
              processing_run_id, machining_run_id, machine_id, started_at, ended_at,
              run_status, program_name, confidence, result_hash, run_projection
            ) VALUES (
              :processing_run_id, :machining_run_id, :machine_id, :started_at, :ended_at,
              :run_status, :program_name, :confidence, :result_hash, CAST(:run_projection AS JSONB)
            )
            """)
        .param("processing_run_id", run.processingRunId())
        .param("machining_run_id", run.machiningRunId())
        .param("machine_id", run.machineId())
        .param("started_at", OffsetDateTime.ofInstant(run.startedAt(), ZoneOffset.UTC))
        .param(
            "ended_at",
            run.endedAt() == null ? null : OffsetDateTime.ofInstant(run.endedAt(), ZoneOffset.UTC))
        .param("run_status", run.status().name())
        .param("program_name", run.programName())
        .param("confidence", run.confidence().name())
        .param("result_hash", run.resultHash())
        .param("run_projection", writeDocument(MachiningRunDocument.from(run)))
        .update();
  }

  private ProcessObservation toObservation(
      UUID replaySessionId,
      long replaySequence,
      OffsetDateTime sourceObservedAt,
      String sourceEventKey,
      String canonicalEnvelope) {
    try {
      JsonNode root = objectMapper.readTree(canonicalEnvelope);
      JsonNode payload = root.path("payload");
      String eventType = payload.path("eventType").asText(null);
      ProcessSignal signal =
          eventType == null ? ProcessSignal.SPINDLE_SPEED : ProcessSignal.valueOf(eventType);
      boolean available = "AVAILABLE".equals(payload.path("availability").asText());
      String textValue =
          signal == ProcessSignal.SPINDLE_SPEED || !available
              ? null
              : payload.path("value").asText();
      BigDecimal numericValue =
          signal == ProcessSignal.SPINDLE_SPEED && available
              ? payload.path("value").decimalValue()
              : null;
      JsonNode source = root.path("provenance").path("source");
      JsonNode transformation = root.path("provenance").path("transformation");
      return new ProcessObservation(
          root.path("machineId").asText(),
          replaySessionId,
          replaySequence,
          sourceObservedAt.toInstant(),
          sourceEventKey,
          signal,
          available,
          textValue,
          numericValue,
          new ObservationProvenance(
              source.path("kind").asText(),
              source.path("provider").asText(),
              source.path("sourceSetId").asText(),
              source.path("artifactId").asText(),
              transformation.path("rawRecordId").asText(),
              transformation.path("mappingVersion").asText(),
              transformation.path("sourceDataItemId").asText()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be decoded", exception);
    }
  }

  private String writeDocument(MachiningRunDocument document) {
    try {
      return objectMapper.writeValueAsString(document);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Machining Run projection cannot be encoded", exception);
    }
  }

  private MachiningRunDocument readDocument(String document) {
    try {
      return objectMapper.readValue(document, MachiningRunDocument.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Stored Machining Run projection cannot be decoded", exception);
    }
  }
}
