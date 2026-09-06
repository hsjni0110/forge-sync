package com.forgesync.factoryapi.toolchange.adapter.outbound.postgres;

import com.forgesync.factoryapi.toolchange.application.ToolNumberHistory;
import com.forgesync.factoryapi.toolchange.domain.ToolNumberObservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresToolNumberHistory implements ToolNumberHistory {
  private final JdbcClient jdbcClient;

  public PostgresToolNumberHistory(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public List<ToolNumberObservation> read(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
        SELECT machine_id, replay_session_id, replay_sequence, source_observed_at,
          source_data_item_id, artifact_id, raw_record_id, mapping_version,
          canonical_envelope #>> '{provenance,source,sourceSetId}' AS source_set_id,
          canonical_envelope #>> '{payload,availability}' AS availability,
          canonical_envelope #>> '{payload,value}' AS tool_number
        FROM canonical_observation_history
        WHERE machine_id = :machine_id
          AND replay_session_id = :replay_session_id
          AND replay_sequence <= :through_replay_sequence
          AND canonical_envelope #>> '{payload,eventType}' = 'TOOL_NUMBER'
        ORDER BY replay_sequence, source_observed_at, source_event_key
        """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .query(
            (rs, row) ->
                new ToolNumberObservation(
                    rs.getString("machine_id"),
                    rs.getObject("replay_session_id", UUID.class),
                    rs.getLong("replay_sequence"),
                    rs.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                    "AVAILABLE".equals(rs.getString("availability"))
                        ? Long.valueOf(rs.getString("tool_number"))
                        : null,
                    rs.getString("source_data_item_id"),
                    rs.getString("source_set_id"),
                    rs.getString("artifact_id"),
                    rs.getString("raw_record_id"),
                    rs.getString("mapping_version")))
        .list();
  }
}
