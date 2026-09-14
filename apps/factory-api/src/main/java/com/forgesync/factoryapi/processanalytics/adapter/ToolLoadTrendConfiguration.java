package com.forgesync.factoryapi.processanalytics.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres.PostgresToolLoadTrendSource;
import com.forgesync.factoryapi.processanalytics.application.ToolLoadTrendService;
import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ToolLoadTrendConfiguration {
  @Bean
  PostgresToolLoadTrendSource toolLoadTrendSource(
      JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new PostgresToolLoadTrendSource(jdbcClient, objectMapper);
  }

  @Bean
  ToolLoadTrendService toolLoadTrendService(PostgresToolLoadTrendSource source) {
    return new ToolLoadTrendService(source, new ToolLoadTrendPolicy());
  }
}
