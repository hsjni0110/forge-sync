package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres.PostgresDowntimeParetoRepository;
import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoService;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalService;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiService;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    name = {
      "forgesync.equipment-state-intervals.enabled",
      "forgesync.utilization-kpis.enabled",
      "forgesync.downtime-pareto.enabled"
    },
    havingValue = "true",
    matchIfMissing = true)
public class DowntimeParetoConfiguration {

  @Bean
  PostgresDowntimeParetoRepository downtimeParetoRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresDowntimeParetoRepository(
        jdbcClient, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  DowntimeParetoService downtimeParetoService(
      UtilizationKpiService utilizationService,
      EquipmentStateIntervalService intervalService,
      PostgresDowntimeParetoRepository repository,
      Clock applicationClock) {
    return new DowntimeParetoService(
        utilizationService,
        intervalService,
        repository,
        repository,
        new DowntimeParetoPolicy(),
        applicationClock);
  }
}
