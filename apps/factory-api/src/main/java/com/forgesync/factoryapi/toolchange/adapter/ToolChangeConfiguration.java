package com.forgesync.factoryapi.toolchange.adapter;

import com.forgesync.factoryapi.toolchange.adapter.outbound.postgres.PostgresToolNumberHistory;
import com.forgesync.factoryapi.toolchange.application.ToolChangeService;
import com.forgesync.factoryapi.toolchange.domain.ToolChangePolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ToolChangeConfiguration {
  @Bean
  PostgresToolNumberHistory toolNumberHistory(JdbcClient jdbcClient) {
    return new PostgresToolNumberHistory(jdbcClient);
  }

  @Bean
  ToolChangeService toolChangeService(PostgresToolNumberHistory history) {
    return new ToolChangeService(history, new ToolChangePolicy());
  }
}
