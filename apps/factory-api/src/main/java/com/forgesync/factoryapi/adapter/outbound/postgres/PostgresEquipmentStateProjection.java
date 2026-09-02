package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentState;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateProjectionPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

final class PostgresEquipmentStateProjection {

  private final JdbcClient jdbcClient;
  private final EquipmentStateProjectionPolicy projectionPolicy;
  private final CanonicalEquipmentStateReader stateReader;

  PostgresEquipmentStateProjection(
      JdbcClient jdbcClient,
      EquipmentStateProjectionPolicy projectionPolicy,
      ObjectMapper objectMapper) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.projectionPolicy = Objects.requireNonNull(projectionPolicy);
    this.stateReader = new CanonicalEquipmentStateReader(objectMapper);
  }

  void project(String machineId, TwinVersion twinVersion, Instant projectedAt) {
    List<StateObservation> latestObservations = findLatestObservations(machineId);
    EquipmentState equipmentState = projectionPolicy.project(latestObservations);
    upsert(machineId, equipmentState, twinVersion, projectedAt);
  }

  private List<StateObservation> findLatestObservations(String machineId) {
    return jdbcClient
        .sql(
            """
            SELECT canonical_envelope::text FROM latest_observation_projection
            WHERE machine_id = :machine_id
            """)
        .param("machine_id", machineId)
        .query(String.class)
        .list()
        .stream()
        .map(stateReader::read)
        .toList();
  }

  private void upsert(
      String machineId,
      EquipmentState equipmentState,
      TwinVersion twinVersion,
      Instant projectedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO equipment_state_projection (
              machine_id, connectivity_state, execution_state, health_state,
              twin_version, projected_at
            ) VALUES (
              :machine_id, :connectivity_state, :execution_state, :health_state,
              :twin_version, :projected_at
            )
            ON CONFLICT (machine_id) DO UPDATE SET
              connectivity_state = EXCLUDED.connectivity_state,
              execution_state = EXCLUDED.execution_state,
              health_state = EXCLUDED.health_state,
              twin_version = EXCLUDED.twin_version,
              projected_at = EXCLUDED.projected_at
            """)
        .param("machine_id", machineId)
        .param("connectivity_state", equipmentState.connectivity().name())
        .param("execution_state", equipmentState.execution().name())
        .param("health_state", equipmentState.health().name())
        .param("twin_version", twinVersion.value())
        .param("projected_at", OffsetDateTime.ofInstant(projectedAt, ZoneOffset.UTC))
        .update();
  }
}
