package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessStore;
import com.forgesync.factoryapi.processanalytics.application.PartCountObservationHistory;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessReport;
import com.forgesync.factoryapi.processanalytics.domain.PartCountObservation;
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

public final class PostgresOperationalEffectivenessRepository
    implements OperationalEffectivenessStore, PartCountObservationHistory {
  private final JdbcClient jdbcClient;
  private final TransactionTemplate transaction;
  private final ObjectMapper objectMapper;
  private final CanonicalPartCountReader reader;

  public PostgresOperationalEffectivenessRepository(
      JdbcClient jdbcClient, TransactionTemplate transaction, ObjectMapper objectMapper) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transaction = Objects.requireNonNull(transaction);
    this.objectMapper = Objects.requireNonNull(objectMapper).copy().findAndRegisterModules();
    reader = new CanonicalPartCountReader(this.objectMapper);
  }

  @Override
  public List<PartCountObservation> readPartCounts(
      String machineId,
      UUID replaySessionId,
      long throughReplaySequence,
      Instant observedFrom,
      Instant observedTo) {
    return jdbcClient
        .sql(
            """
        SELECT replay_sequence, source_observed_at, source_event_key, canonical_envelope
        FROM canonical_observation_history
        WHERE machine_id = :machine_id AND replay_session_id = :replay_session_id
          AND replay_sequence <= :through_replay_sequence
          AND source_observed_at >= :observed_from AND source_observed_at <= :observed_to
          AND observation_kind = 'EVENT'
          AND canonical_envelope #>> '{payload,eventType}' = 'PART_COUNT'
        ORDER BY replay_sequence, source_observed_at, source_event_key
        """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .param("observed_from", OffsetDateTime.ofInstant(observedFrom, ZoneOffset.UTC))
        .param("observed_to", OffsetDateTime.ofInstant(observedTo, ZoneOffset.UTC))
        .query(
            (rs, row) -> {
              long sequence = rs.getLong("replay_sequence");
              Instant observedAt =
                  rs.getObject("source_observed_at", OffsetDateTime.class).toInstant();
              String eventKey = rs.getString("source_event_key");
              return reader
                  .read(rs.getString("canonical_envelope"))
                  .map(
                      value ->
                          new PartCountObservation(
                              sequence, observedAt, eventKey, value.isAvailable(), value.value()))
                  .orElse(null);
            })
        .list()
        .stream()
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public Optional<StoredOperationalEffectiveness> find(String processingRunId) {
    return jdbcClient
        .sql(
            """
        SELECT processing_run_id, created_at, report
        FROM operational_effectiveness_processing WHERE processing_run_id = :id
        """)
        .param("id", processingRunId)
        .query(
            (rs, row) ->
                new StoredOperationalEffectiveness(
                    rs.getString("processing_run_id"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    readReport(rs.getString("report"))))
        .optional();
  }

  @Override
  public boolean preserve(StoredOperationalEffectiveness processing) {
    Boolean created =
        transaction.execute(
            status -> {
              try {
                insert(processing);
                return true;
              } catch (DuplicateKeyException duplicate) {
                return false;
              }
            });
    return Boolean.TRUE.equals(created);
  }

  private void insert(StoredOperationalEffectiveness processing) {
    var report = processing.report();
    jdbcClient
        .sql(
            """
        INSERT INTO operational_effectiveness_processing (
          processing_run_id, machine_id, replay_session_id, through_replay_sequence,
          policy_version, utilization_processing_run_id, cycle_feature_processing_run_id,
          observed_from, observed_to, input_hash, result_hash, report, created_at
        ) VALUES (:id, :machine, :session, :sequence, :version, :utilization, :cycles,
          :observed_from, :observed_to, :input_hash, :result_hash, CAST(:report AS jsonb), :created_at)
        """)
        .param("id", processing.processingRunId())
        .param("machine", report.machineId())
        .param("session", report.replaySessionId())
        .param("sequence", report.throughReplaySequence())
        .param("version", report.policyVersion())
        .param("utilization", report.utilizationProcessingRunId())
        .param("cycles", report.cycleFeatureProcessingRunId())
        .param("observed_from", OffsetDateTime.ofInstant(report.observedFrom(), ZoneOffset.UTC))
        .param("observed_to", OffsetDateTime.ofInstant(report.observedTo(), ZoneOffset.UTC))
        .param("input_hash", report.inputHash())
        .param("result_hash", report.resultHash())
        .param("report", writeReport(report))
        .param("created_at", OffsetDateTime.ofInstant(processing.createdAt(), ZoneOffset.UTC))
        .update();
  }

  private String writeReport(OperationalEffectivenessReport report) {
    try {
      return objectMapper.writeValueAsString(report);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Operational effectiveness report cannot be stored", exception);
    }
  }

  private OperationalEffectivenessReport readReport(String document) {
    try {
      return objectMapper.readValue(document, OperationalEffectivenessReport.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Stored operational effectiveness report cannot be read", exception);
    }
  }
}
