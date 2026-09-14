package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.application.ToolLoadTrendInputNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.ToolLoadTrendSource;
import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendReport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresToolLoadTrendSource implements ToolLoadTrendSource {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public PostgresToolLoadTrendSource(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolLoadTrendInput read(String machineId, String machiningRunProcessingRunId) {
    ProcessingScope scope = readScope(machineId, machiningRunProcessingRunId);
    return new ToolLoadTrendInput(
        scope.machineId(),
        scope.replaySessionId().toString(),
        scope.throughReplaySequence(),
        machiningRunProcessingRunId,
        readRuns(scope.machineId(), machiningRunProcessingRunId),
        readToolNumbers(scope),
        readLoads(scope));
  }

  private ProcessingScope readScope(String machineId, String processingRunId) {
    try {
      return jdbcClient
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
      throw new ToolLoadTrendInputNotFoundException(processingRunId, missing);
    }
  }

  private List<ToolLoadTrendReport.MachiningRunInput> readRuns(
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
            (rs, row) -> {
              OffsetDateTime endedAt = rs.getObject("ended_at", OffsetDateTime.class);
              return new ToolLoadTrendReport.MachiningRunInput(
                  rs.getString("machining_run_id"),
                  rs.getString("program_name"),
                  rs.getString("run_status"),
                  rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                  endedAt == null ? null : endedAt.toInstant());
            })
        .list();
  }

  private List<ToolLoadTrendReport.ToolNumberObservation> readToolNumbers(ProcessingScope scope) {
    return jdbcClient
        .sql(
            """
            SELECT replay_sequence, source_observed_at, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine AND replay_session_id = :session
              AND replay_sequence <= :sequence
              AND canonical_envelope #>> '{payload,eventType}' = 'TOOL_NUMBER'
            ORDER BY source_observed_at, replay_sequence, source_event_key
            """)
        .param("machine", scope.machineId())
        .param("session", scope.replaySessionId())
        .param("sequence", scope.throughReplaySequence())
        .query(
            (rs, row) -> {
              var payload = document(rs.getString("canonical_envelope")).path("payload");
              boolean available = "AVAILABLE".equals(payload.path("availability").asText());
              return new ToolLoadTrendReport.ToolNumberObservation(
                  available,
                  available ? payload.path("value").intValue() : null,
                  rs.getLong("replay_sequence"),
                  rs.getObject("source_observed_at", OffsetDateTime.class).toInstant());
            })
        .list();
  }

  private List<ToolLoadTrendReport.LoadObservation> readLoads(ProcessingScope scope) {
    return jdbcClient
        .sql(
            """
            SELECT component_id, replay_sequence, source_observed_at, source_event_key,
              raw_record_id, source_data_item_id, mapping_version, canonical_envelope
            FROM canonical_observation_history
            WHERE machine_id = :machine AND replay_session_id = :session
              AND replay_sequence <= :sequence
              AND canonical_envelope #>> '{payload,metric}' = 'LOAD'
            ORDER BY source_observed_at, replay_sequence, source_event_key
            """)
        .param("machine", scope.machineId())
        .param("session", scope.replaySessionId())
        .param("sequence", scope.throughReplaySequence())
        .query(
            (rs, row) -> {
              var root = document(rs.getString("canonical_envelope"));
              var payload = root.path("payload");
              var source = root.path("provenance").path("source");
              boolean available = "AVAILABLE".equals(payload.path("availability").asText());
              return new ToolLoadTrendReport.LoadObservation(
                  available,
                  available ? payload.path("value").decimalValue() : null,
                  payload.path("unit").asText(null),
                  rs.getString("component_id"),
                  rs.getLong("replay_sequence"),
                  rs.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                  rs.getString("source_event_key"),
                  rs.getString("raw_record_id"),
                  rs.getString("source_data_item_id"),
                  rs.getString("mapping_version"),
                  source.path("kind").asText("UNKNOWN"),
                  source.path("provider").asText("UNKNOWN"),
                  source.path("sourceSetId").asText("UNKNOWN"));
            })
        .list();
  }

  private com.fasterxml.jackson.databind.JsonNode document(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be read", exception);
    }
  }

  private record ProcessingScope(
      String machineId, UUID replaySessionId, long throughReplaySequence) {}
}
