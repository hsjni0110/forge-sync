package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres.PostgresReplayProjectionActivator;
import com.forgesync.factoryapi.equipmenttwin.application.ActivateReplayProjection;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateProjectionPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessPolicy;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class EquipmentTwinConfiguration {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  @Bean
  EquipmentStateProjectionPolicy equipmentStateProjectionPolicy() {
    return new EquipmentStateProjectionPolicy();
  }

  @Bean
  FreshnessPolicy freshnessPolicy(
      @Value("${forgesync.twin.freshness.fresh-max-age:2s}") Duration freshMaxAge,
      @Value("${forgesync.twin.freshness.lagging-max-age:10s}") Duration laggingMaxAge) {
    return new FreshnessPolicy(freshMaxAge, laggingMaxAge);
  }

  @Bean
  @ConditionalOnProperty(
      name = "forgesync.replay.enabled",
      havingValue = "true",
      matchIfMissing = true)
  ActivateReplayProjection activateReplayProjection(
      JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
    return new PostgresReplayProjectionActivator(jdbcClient, transactionManager);
  }
}
