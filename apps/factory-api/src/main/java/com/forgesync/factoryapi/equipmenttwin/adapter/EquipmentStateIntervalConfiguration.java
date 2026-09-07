package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres.PostgresEquipmentStateIntervalRepository;
import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalService;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.equipment-state-intervals.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EquipmentStateIntervalConfiguration {

  @Bean
  PostgresEquipmentStateIntervalRepository equipmentStateIntervalRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    return new PostgresEquipmentStateIntervalRepository(
        jdbcClient, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  EquipmentStateIntervalService equipmentStateIntervalService(
      PostgresEquipmentStateIntervalRepository repository, Clock applicationClock) {
    return new EquipmentStateIntervalService(
        repository, repository, new EquipmentStateIntervalPolicy(), applicationClock);
  }
}
