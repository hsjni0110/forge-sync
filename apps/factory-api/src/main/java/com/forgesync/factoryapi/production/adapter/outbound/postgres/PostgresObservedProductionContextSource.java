package com.forgesync.factoryapi.production.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.production.application.ObservedProductionContextSource;
import com.forgesync.factoryapi.production.application.ProductionContextInputNotFoundException;
import com.forgesync.factoryapi.production.domain.ObservedProductionContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresObservedProductionContextSource
    implements ObservedProductionContextSource {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public PostgresObservedProductionContextSource(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public ProductionContextInput read(String machineId, String processingRunId) {
    ProcessingScope scope;
    try {
      scope =
          jdbcClient
              .sql(
                  """
                  SELECT machine_id, replay_session_id, through_replay_sequence
                  FROM process_analytics_processing_run
                  WHERE processing_run_id = :id AND machine_id = :machine
                  """)
              .param("id", processingRunId)
              .param("machine", machineId)
              .query(
                  (rs, row) ->
                      new ProcessingScope(
                          rs.getString("machine_id"),
                          rs.getObject("replay_session_id", UUID.class),
                          rs.getLong("through_replay_sequence")))
              .single();
    } catch (EmptyResultDataAccessException missing) {
      throw new ProductionContextInputNotFoundException(processingRunId, missing);
    }
    return new ProductionContextInput(
        scope.machineId(),
        scope.replaySessionId().toString(),
        scope.throughReplaySequence(),
        processingRunId,
        readPrograms(scope),
        readRuns(machineId, processingRunId),
        readPartCounts(scope));
  }

  private List<ObservedProductionContext.ProgramObservation> readPrograms(ProcessingScope scope) {
    return jdbcClient
        .sql(
            """
            SELECT replay_sequence, source_observed_at, source_event_key, source_data_item_id,
              raw_record_id, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine AND replay_session_id = :session
              AND replay_sequence <= :sequence
              AND canonical_envelope #>> '{payload,eventType}' IN ('PROGRAM', 'SUBPROGRAM')
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine", scope.machineId())
        .param("session", scope.replaySessionId())
        .param("sequence", scope.throughReplaySequence())
        .query(
            (rs, row) -> {
              Payload payload = payload(rs.getString("canonical_envelope"));
              boolean available = "AVAILABLE".equals(payload.availability());
              return new ObservedProductionContext.ProgramObservation(
                  "SUBPROGRAM".equals(payload.eventType()) ? "SUBPROGRAM" : "MAIN",
                  available,
                  available ? payload.value() : null,
                  rs.getLong("replay_sequence"),
                  rs.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                  rs.getString("source_event_key"),
                  rs.getString("raw_record_id"),
                  rs.getString("source_data_item_id"));
            })
        .list();
  }

  private List<ObservedProductionContext.ObservedMachiningRun> readRuns(
      String machineId, String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT machining_run_id, program_name, run_status, started_at, ended_at
            FROM machining_run_projection
            WHERE machine_id = :machine AND processing_run_id = :id
            ORDER BY started_at, machining_run_id
            """)
        .param("machine", machineId)
        .param("id", processingRunId)
        .query(
            (rs, row) ->
                new ObservedProductionContext.ObservedMachiningRun(
                    rs.getString("machining_run_id"),
                    rs.getString("program_name"),
                    rs.getString("run_status"),
                    rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("ended_at", OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("ended_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  private List<ObservedProductionContext.PartCountObservation> readPartCounts(
      ProcessingScope scope) {
    return jdbcClient
        .sql(
            """
            SELECT replay_sequence, source_observed_at, source_event_key, source_data_item_id,
              raw_record_id, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine AND replay_session_id = :session
              AND replay_sequence <= :sequence
              AND canonical_envelope #>> '{payload,eventType}' = 'PART_COUNT'
            ORDER BY replay_sequence, source_observed_at, source_event_key
            """)
        .param("machine", scope.machineId())
        .param("session", scope.replaySessionId())
        .param("sequence", scope.throughReplaySequence())
        .query(
            (rs, row) -> {
              Payload payload = payload(rs.getString("canonical_envelope"));
              boolean available = "AVAILABLE".equals(payload.availability());
              return new ObservedProductionContext.PartCountObservation(
                  available,
                  available ? new BigDecimal(payload.value()) : null,
                  rs.getLong("replay_sequence"),
                  rs.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                  rs.getString("source_event_key"),
                  rs.getString("raw_record_id"),
                  rs.getString("source_data_item_id"));
            })
        .list();
  }

  private Payload payload(String document) {
    try {
      var payload = objectMapper.readTree(document).path("payload");
      return new Payload(
          payload.path("eventType").asText(),
          payload.path("availability").asText(),
          payload.path("value").asText(null));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be read", exception);
    }
  }

  private record ProcessingScope(
      String machineId, UUID replaySessionId, long throughReplaySequence) {}

  private record Payload(String eventType, String availability, String value) {}
}
