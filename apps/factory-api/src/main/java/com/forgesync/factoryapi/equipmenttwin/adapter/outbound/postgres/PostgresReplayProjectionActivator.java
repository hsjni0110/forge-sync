package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.forgesync.factoryapi.equipmenttwin.application.ActivateReplayProjection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresReplayProjectionActivator implements ActivateReplayProjection {
  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;

  public PostgresReplayProjectionActivator(
      JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Override
  public void activate(String machineId, UUID replaySessionId, Instant activatedAt) {
    transactionTemplate.executeWithoutResult(
        status -> {
          jdbcClient
              .sql(
                  """
                  INSERT INTO active_replay_projection (machine_id, replay_session_id, activated_at)
                  VALUES (:machine_id, :replay_session_id, :activated_at)
                  ON CONFLICT (machine_id) DO UPDATE SET
                    replay_session_id = EXCLUDED.replay_session_id,
                    activated_at = EXCLUDED.activated_at
                  """)
              .param("machine_id", machineId)
              .param("replay_session_id", replaySessionId)
              .param("activated_at", OffsetDateTime.ofInstant(activatedAt, ZoneOffset.UTC))
              .update();
          jdbcClient
              .sql("DELETE FROM equipment_state_projection WHERE machine_id = :machine_id")
              .param("machine_id", machineId)
              .update();
          jdbcClient
              .sql("DELETE FROM latest_observation_projection WHERE machine_id = :machine_id")
              .param("machine_id", machineId)
              .update();
        });
  }
}
