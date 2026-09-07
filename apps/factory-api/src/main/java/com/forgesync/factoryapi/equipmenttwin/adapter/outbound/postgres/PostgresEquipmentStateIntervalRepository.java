package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalStore;
import com.forgesync.factoryapi.equipmenttwin.application.StateIntervalObservationHistory;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateInterval;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.IntervalBoundaryEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.SignalCoverage;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import java.time.Duration;
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

public final class PostgresEquipmentStateIntervalRepository
    implements StateIntervalObservationHistory, EquipmentStateIntervalStore {

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final CanonicalStateSignalReader signalReader;

  public PostgresEquipmentStateIntervalRepository(
      JdbcClient jdbcClient, TransactionTemplate transactionTemplate, ObjectMapper objectMapper) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.signalReader = new CanonicalStateSignalReader(objectMapper);
  }

  @Override
  public List<StateSignalObservation> readStateObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
            SELECT replay_sequence, source_observed_at, source_event_key, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine_id
              AND replay_session_id = :replay_session_id
              AND replay_sequence <= :through_replay_sequence
              AND canonical_envelope #>> '{payload,eventType}' IN (
                'EXECUTION', 'CONTROLLER_MODE', 'POWER_STATE', 'EMERGENCY_STOP'
              )
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .query(
            (resultSet, rowNumber) -> {
              // Read every column before branching: an Optional mapper cannot throw SQLException.
              long replaySequence = resultSet.getLong("replay_sequence");
              Instant observedAt =
                  resultSet.getObject("source_observed_at", OffsetDateTime.class).toInstant();
              String sourceEventKey = resultSet.getString("source_event_key");
              return signalReader
                  .read(resultSet.getString("canonical_envelope"))
                  .map(
                      signal ->
                          new StateSignalObservation(
                              machineId,
                              replaySessionId,
                              replaySequence,
                              observedAt,
                              sourceEventKey,
                              signal.signal(),
                              signal.isAvailable(),
                              signal.value()))
                  .orElse(null);
            })
        .list()
        .stream()
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public boolean preserve(StoredIntervalProcessing processing) {
    Boolean created =
        transactionTemplate.execute(
            status -> {
              try {
                insertProcessing(processing);
              } catch (DuplicateKeyException duplicate) {
                return false;
              }
              for (EquipmentStateInterval interval : processing.report().intervals()) {
                insertInterval(processing.processingRunId(), interval);
              }
              return true;
            });
    return Boolean.TRUE.equals(created);
  }

  @Override
  public Optional<StoredIntervalProcessing> findProcessingRun(String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              interval_rule_version, observed_from, observed_to, input_hash,
              input_observation_count, result_hash, coverage, created_at
            FROM equipment_state_interval_processing
            WHERE processing_run_id = :processing_run_id
            """)
        .param("processing_run_id", processingRunId)
        .query(
            (resultSet, rowNumber) ->
                new StoredIntervalProcessing(
                    resultSet.getString("processing_run_id"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                    new EquipmentStateIntervalReport(
                        resultSet.getString("interval_rule_version"),
                        resultSet.getString("machine_id"),
                        resultSet.getObject("replay_session_id", UUID.class),
                        resultSet.getLong("through_replay_sequence"),
                        resultSet.getObject("observed_from", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("observed_to", OffsetDateTime.class).toInstant(),
                        resultSet.getInt("input_observation_count"),
                        resultSet.getString("input_hash"),
                        resultSet.getString("result_hash"),
                        readIntervals(resultSet.getString("processing_run_id")),
                        readCoverage(resultSet.getString("coverage")))))
        .optional();
  }

  @Override
  public Optional<StoredIntervalProcessing> findLatestForSession(
      String machineId, UUID replaySessionId) {
    return jdbcClient
        .sql(
            """
            SELECT processing_run_id
            FROM equipment_state_interval_processing
            WHERE machine_id = :machine_id AND replay_session_id = :replay_session_id
            ORDER BY through_replay_sequence DESC, created_at DESC
            LIMIT 1
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .query(String.class)
        .optional()
        .flatMap(this::findProcessingRun);
  }

  private void insertProcessing(StoredIntervalProcessing processing) {
    EquipmentStateIntervalReport report = processing.report();
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_state_interval_processing (
              processing_run_id, machine_id, replay_session_id, through_replay_sequence,
              interval_rule_version, observed_from, observed_to, input_hash,
              input_observation_count, result_hash, coverage, created_at
            ) VALUES (
              :processing_run_id, :machine_id, :replay_session_id, :through_replay_sequence,
              :interval_rule_version, :observed_from, :observed_to, :input_hash,
              :input_observation_count, :result_hash, CAST(:coverage AS jsonb), :created_at
            )
            """)
        .param("processing_run_id", processing.processingRunId())
        .param("machine_id", report.machineId())
        .param("replay_session_id", report.replaySessionId())
        .param("through_replay_sequence", report.throughReplaySequence())
        .param("interval_rule_version", report.ruleVersion())
        .param("observed_from", asUtcOffset(report.observedFrom()))
        .param("observed_to", asUtcOffset(report.observedTo()))
        .param("input_hash", report.inputHash())
        .param("input_observation_count", report.inputObservationCount())
        .param("result_hash", report.resultHash())
        .param("coverage", writeCoverage(report.coverage()))
        .param("created_at", asUtcOffset(processing.createdAt()))
        .update();
  }

  private void insertInterval(String processingRunId, EquipmentStateInterval interval) {
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_state_interval (
              processing_run_id, signal, started_at, ended_at, state_value,
              start_replay_sequence, start_source_event_key,
              end_replay_sequence, end_source_event_key
            ) VALUES (
              :processing_run_id, :signal, :started_at, :ended_at, :state_value,
              :start_replay_sequence, :start_source_event_key,
              :end_replay_sequence, :end_source_event_key
            )
            """)
        .param("processing_run_id", processingRunId)
        .param("signal", interval.signal().name())
        .param("started_at", asUtcOffset(interval.startedAt()))
        .param("ended_at", interval.endedAt() == null ? null : asUtcOffset(interval.endedAt()))
        .param("state_value", interval.value())
        .param("start_replay_sequence", interval.startEvidence().replaySequence())
        .param("start_source_event_key", interval.startEvidence().sourceEventKey())
        .param(
            "end_replay_sequence",
            interval.endEvidence() == null ? null : interval.endEvidence().replaySequence())
        .param(
            "end_source_event_key",
            interval.endEvidence() == null ? null : interval.endEvidence().sourceEventKey())
        .update();
  }

  private List<EquipmentStateInterval> readIntervals(String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT signal, started_at, ended_at, state_value, start_replay_sequence,
              start_source_event_key, end_replay_sequence, end_source_event_key
            FROM equipment_state_interval
            WHERE processing_run_id = :processing_run_id
            ORDER BY started_at, signal, start_source_event_key
            """)
        .param("processing_run_id", processingRunId)
        .query(
            (resultSet, rowNumber) -> {
              OffsetDateTime endedAt = resultSet.getObject("ended_at", OffsetDateTime.class);
              String endKey = resultSet.getString("end_source_event_key");
              return new EquipmentStateInterval(
                  StateSignal.valueOf(resultSet.getString("signal")),
                  resultSet.getString("state_value"),
                  resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
                  endedAt == null ? null : endedAt.toInstant(),
                  new IntervalBoundaryEvidence(
                      resultSet.getLong("start_replay_sequence"),
                      resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
                      resultSet.getString("start_source_event_key")),
                  endKey == null
                      ? null
                      : new IntervalBoundaryEvidence(
                          resultSet.getLong("end_replay_sequence"), endedAt.toInstant(), endKey));
            })
        .list();
  }

  private String writeCoverage(List<SignalCoverage> coverage) {
    try {
      List<CoverageDocument> documents =
          coverage.stream()
              .map(
                  entry ->
                      new CoverageDocument(
                          entry.signal().name(),
                          entry.closedDuration().toString(),
                          entry.leadingUnobserved().toString(),
                          entry.openSince() == null ? null : entry.openSince().toString(),
                          entry.intervalCount()))
              .toList();
      return objectMapper.writeValueAsString(documents);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Coverage cannot be serialized", exception);
    }
  }

  private List<SignalCoverage> readCoverage(String coverage) {
    try {
      List<CoverageDocument> documents =
          objectMapper.readerForListOf(CoverageDocument.class).readValue(coverage);
      return documents.stream()
          .map(
              document ->
                  new SignalCoverage(
                      StateSignal.valueOf(document.signal()),
                      Duration.parse(document.closedDuration()),
                      Duration.parse(document.leadingUnobserved()),
                      document.openSince() == null ? null : Instant.parse(document.openSince()),
                      document.intervalCount()))
          .toList();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored coverage cannot be read", exception);
    }
  }

  private static OffsetDateTime asUtcOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  record CoverageDocument(
      String signal,
      String closedDuration,
      String leadingUnobserved,
      String openSince,
      int intervalCount) {}
}
