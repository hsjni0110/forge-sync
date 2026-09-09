package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.ConditionEvidenceHistory;
import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoStore;
import com.forgesync.factoryapi.equipmenttwin.domain.ConditionEvidenceObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresDowntimeParetoRepository
    implements ConditionEvidenceHistory, DowntimeParetoStore {
  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final CanonicalConditionEvidenceReader conditionReader;

  public PostgresDowntimeParetoRepository(
      JdbcClient jdbcClient, TransactionTemplate transactionTemplate, ObjectMapper objectMapper) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    this.objectMapper = Objects.requireNonNull(objectMapper).copy().findAndRegisterModules();
    this.conditionReader = new CanonicalConditionEvidenceReader(this.objectMapper);
  }

  @Override
  public List<ConditionEvidenceObservation> readAttentionConditions(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
            SELECT replay_sequence, source_observed_at, source_event_key, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine_id
              AND replay_session_id = :replay_session_id
              AND replay_sequence <= :through_replay_sequence
              AND observation_kind = 'CONDITION'
              AND canonical_envelope #>> '{payload,level}' IN ('WARNING', 'FAULT')
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .query(
            (resultSet, rowNumber) -> {
              long replaySequence = resultSet.getLong("replay_sequence");
              Instant observedAt =
                  resultSet.getObject("source_observed_at", OffsetDateTime.class).toInstant();
              String sourceEventKey = resultSet.getString("source_event_key");
              return conditionReader
                  .read(resultSet.getString("canonical_envelope"))
                  .map(
                      condition ->
                          new ConditionEvidenceObservation(
                              machineId,
                              replaySessionId,
                              replaySequence,
                              observedAt,
                              sourceEventKey,
                              condition.componentId(),
                              condition.conditionType(),
                              condition.level(),
                              condition.nativeCode(),
                              condition.message()))
                  .orElse(null);
            })
        .list()
        .stream()
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public Optional<StoredDowntimePareto> findProcessingRun(String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT processing_run_id, created_at, report
            FROM downtime_pareto_processing
            WHERE processing_run_id = :processing_run_id
            """)
        .param("processing_run_id", processingRunId)
        .query(
            (resultSet, rowNumber) ->
                new StoredDowntimePareto(
                    resultSet.getString("processing_run_id"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                    readReport(resultSet.getString("report"))))
        .optional();
  }

  @Override
  public boolean preserve(StoredDowntimePareto processing) {
    Boolean created =
        transactionTemplate.execute(
            status -> {
              try {
                insertProcessing(processing);
                return true;
              } catch (DuplicateKeyException duplicate) {
                return false;
              }
            });
    return Boolean.TRUE.equals(created);
  }

  private void insertProcessing(StoredDowntimePareto processing) {
    DowntimeParetoReport report = processing.report();
    jdbcClient
        .sql(
            """
            INSERT INTO downtime_pareto_processing (
              processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              rule_version, utilization_processing_run_id, interval_processing_run_id,
              observed_from, observed_to, input_hash, result_hash, report, created_at
            ) VALUES (
              :processing_run_id, :machine_id, :replay_session_id, :through_replay_sequence,
              :rule_version, :utilization_processing_run_id, :interval_processing_run_id,
              :observed_from, :observed_to, :input_hash, :result_hash,
              CAST(:report AS jsonb), :created_at
            )
            """)
        .param("processing_run_id", processing.processingRunId())
        .param("machine_id", report.machineId())
        .param("replay_session_id", report.replaySessionId())
        .param("through_replay_sequence", report.throughReplaySequence())
        .param("rule_version", report.ruleVersion())
        .param("utilization_processing_run_id", report.utilizationProcessingRunId())
        .param("interval_processing_run_id", report.intervalProcessingRunId())
        .param("observed_from", OffsetDateTime.ofInstant(report.observedFrom(), ZoneOffset.UTC))
        .param("observed_to", OffsetDateTime.ofInstant(report.observedTo(), ZoneOffset.UTC))
        .param("input_hash", report.inputHash())
        .param("result_hash", report.resultHash())
        .param("report", writeReport(report))
        .param("created_at", OffsetDateTime.ofInstant(processing.createdAt(), ZoneOffset.UTC))
        .update();
  }

  private String writeReport(DowntimeParetoReport report) {
    try {
      return objectMapper.writeValueAsString(report);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Downtime Pareto report cannot be stored", exception);
    }
  }

  private DowntimeParetoReport readReport(String document) {
    try {
      return objectMapper.readValue(document, DowntimeParetoReport.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Stored downtime Pareto report cannot be read", exception);
    }
  }
}
