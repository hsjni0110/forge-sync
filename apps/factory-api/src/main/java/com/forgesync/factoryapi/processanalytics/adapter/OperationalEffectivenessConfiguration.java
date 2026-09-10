package com.forgesync.factoryapi.processanalytics.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.FindUtilizationKpis;
import com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres.PostgresCycleFeatureRepository;
import com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres.PostgresMachiningRunRepository;
import com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres.PostgresOperationalEffectivenessRepository;
import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessService;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessPolicy;
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
      "forgesync.process-analytics.enabled",
      "forgesync.equipment-state-intervals.enabled",
      "forgesync.utilization-kpis.enabled"
    },
    havingValue = "true",
    matchIfMissing = true)
public class OperationalEffectivenessConfiguration {
  @Bean
  PostgresOperationalEffectivenessRepository operationalEffectivenessRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresOperationalEffectivenessRepository(
        jdbcClient, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  OperationalEffectivenessService operationalEffectivenessService(
      FindUtilizationKpis utilizationSource,
      PostgresCycleFeatureRepository cycleFeatureRepository,
      PostgresMachiningRunRepository machiningRunRepository,
      PostgresOperationalEffectivenessRepository repository,
      Clock applicationClock) {
    return new OperationalEffectivenessService(
        new EquipmentTwinUtilizationEvidenceAdapter(utilizationSource),
        cycleFeatureRepository,
        machiningRunRepository,
        repository,
        repository,
        new OperationalEffectivenessPolicy(),
        applicationClock);
  }
}
