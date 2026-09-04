package com.forgesync.factoryapi.processanalytics.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres.PostgresMachiningRunRepository;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunService;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunSegmentationPolicy;
import com.forgesync.factoryapi.processanalytics.domain.ProcessFactSourcePolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProcessAnalyticsConfiguration {

  @Bean
  @ConditionalOnBean(JdbcClient.class)
  PostgresMachiningRunRepository machiningRunRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresMachiningRunRepository(jdbcClient, objectMapper, transactionManager);
  }

  @Bean
  @ConditionalOnBean(PostgresMachiningRunRepository.class)
  MachiningRunService machiningRunService(
      PostgresMachiningRunRepository repository, Clock applicationClock) {
    return new MachiningRunService(
        repository,
        repository,
        new MachiningRunSegmentationPolicy(),
        new ProcessFactSourcePolicy(),
        applicationClock);
  }
}
