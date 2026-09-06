package com.forgesync.factoryapi.toolpath.adapter;

import com.forgesync.factoryapi.toolpath.adapter.outbound.postgres.PostgresAxisPositionHistory;
import com.forgesync.factoryapi.toolpath.application.ObservedToolpathService;
import com.forgesync.factoryapi.toolpath.domain.ObservedToolpathPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservedToolpathConfiguration {
  @Bean
  PostgresAxisPositionHistory axisPositionHistory(JdbcClient jdbcClient) {
    return new PostgresAxisPositionHistory(jdbcClient);
  }

  @Bean
  ObservedToolpathService observedToolpathService(PostgresAxisPositionHistory history) {
    return new ObservedToolpathService(history, new ObservedToolpathPolicy(100, 2048));
  }
}
