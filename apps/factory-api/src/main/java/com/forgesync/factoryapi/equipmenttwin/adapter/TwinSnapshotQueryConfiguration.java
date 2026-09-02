package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres.PostgresTwinProjectionReader;
import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshotService;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionReader;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.twin.query.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TwinSnapshotQueryConfiguration {

  @Bean
  TwinProjectionReader twinProjectionReader(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresTwinProjectionReader(jdbcClient, objectMapper, transactionManager);
  }

  @Bean
  GetOperationalTwinSnapshot getOperationalTwinSnapshot(
      TwinProjectionReader twinProjectionReader,
      FreshnessPolicy freshnessPolicy,
      Clock applicationClock) {
    return new OperationalTwinSnapshotService(
        twinProjectionReader, freshnessPolicy, applicationClock);
  }
}
