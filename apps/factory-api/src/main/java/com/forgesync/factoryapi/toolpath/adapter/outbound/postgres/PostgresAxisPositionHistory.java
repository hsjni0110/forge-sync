package com.forgesync.factoryapi.toolpath.adapter.outbound.postgres;

import com.forgesync.factoryapi.toolpath.application.AxisPositionHistory;
import com.forgesync.factoryapi.toolpath.domain.AxisPositionObservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresAxisPositionHistory implements AxisPositionHistory {
  private final JdbcClient jdbcClient;

  public PostgresAxisPositionHistory(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public List<AxisPositionObservation> read(
      String machineId, UUID replaySessionId, long throughSequence) {
    return jdbcClient
        .sql(
            """
        SELECT replay_sequence, source_observed_at, source_data_item_id, artifact_id,
          raw_record_id, mapping_version,
          canonical_envelope #>> '{provenance,source,sourceSetId}' AS source_set_id,
          canonical_envelope #>> '{payload,availability}' AS availability,
          canonical_envelope #>> '{payload,value}' AS position_value,
          canonical_envelope #>> '{payload,unit}' AS position_unit
        FROM canonical_observation_history
        WHERE machine_id = :machine_id
          AND replay_session_id = :replay_session_id
          AND replay_sequence <= :through_sequence
          AND canonical_envelope #>> '{payload,metric}' = 'POSITION'
          AND source_data_item_id IN ('Mazak01-X_1', 'Mazak01-Y_1', 'Mazak01-Z_1')
        ORDER BY replay_sequence, source_observed_at, source_event_key
        """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_sequence", throughSequence)
        .query(
            (rs, row) ->
                new AxisPositionObservation(
                    axis(rs.getString("source_data_item_id")),
                    rs.getLong("replay_sequence"),
                    rs.getObject("source_observed_at", OffsetDateTime.class).toInstant(),
                    rs.getString("availability"),
                    rs.getString("position_value") == null
                        ? null
                        : Double.valueOf(rs.getString("position_value")),
                    rs.getString("position_unit"),
                    rs.getString("source_data_item_id"),
                    rs.getString("source_set_id"),
                    rs.getString("artifact_id"),
                    rs.getString("raw_record_id"),
                    rs.getString("mapping_version")))
        .list();
  }

  private static String axis(String sourceDataItemId) {
    return sourceDataItemId.substring("Mazak01-".length(), "Mazak01-".length() + 1);
  }
}
