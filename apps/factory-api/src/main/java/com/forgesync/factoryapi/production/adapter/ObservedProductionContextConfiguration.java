package com.forgesync.factoryapi.production.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.production.adapter.outbound.postgres.PostgresObservedProductionContextSource;
import com.forgesync.factoryapi.production.application.ObservedProductionContextService;
import com.forgesync.factoryapi.production.domain.ObservedProductionContextPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservedProductionContextConfiguration {
  @Bean
  PostgresObservedProductionContextSource observedProductionContextSource(
      JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new PostgresObservedProductionContextSource(jdbcClient, objectMapper);
  }

  @Bean
  ObservedProductionContextService observedProductionContextService(
      PostgresObservedProductionContextSource source) {
    return new ObservedProductionContextService(source, new ObservedProductionContextPolicy());
  }
}
