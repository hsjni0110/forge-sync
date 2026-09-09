package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres.PostgresUtilizationKpiRepository;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalService;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiService;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    name = {"forgesync.equipment-state-intervals.enabled", "forgesync.utilization-kpis.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class UtilizationKpiConfiguration {

  @Bean
  PostgresUtilizationKpiRepository utilizationKpiRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresUtilizationKpiRepository(
        jdbcClient, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  UtilizationKpiService utilizationKpiService(
      EquipmentStateIntervalService intervalService,
      PostgresUtilizationKpiRepository repository,
      Clock applicationClock) {
    return new UtilizationKpiService(
        intervalService, repository, repository, new UtilizationKpiPolicy(), applicationClock);
  }
}
