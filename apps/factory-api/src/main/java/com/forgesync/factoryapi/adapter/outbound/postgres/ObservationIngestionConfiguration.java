package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.application.IngestObservation;
import com.forgesync.factoryapi.application.ObservationIngress;
import com.forgesync.factoryapi.application.ObservationTransaction;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionNotifier;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateProjectionPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationOrderingPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.ingestion.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservationIngestionConfiguration {

  @Bean
  ObservationTransaction observationTransaction(
      JdbcClient jdbcClient,
      PlatformTransactionManager transactionManager,
      ObservationOrderingPolicy observationOrderingPolicy,
      EquipmentStateProjectionPolicy equipmentStateProjectionPolicy,
      ObjectMapper objectMapper,
      ObjectProvider<TwinProjectionNotifier> twinProjectionNotifier) {
    PostgresEquipmentStateProjection equipmentStateProjection =
        new PostgresEquipmentStateProjection(
            jdbcClient, equipmentStateProjectionPolicy, objectMapper);
    return new PostgresObservationTransaction(
        jdbcClient,
        transactionManager,
        observationOrderingPolicy,
        equipmentStateProjection,
        twinProjectionNotifier.getIfAvailable(TwinProjectionNotifier::noOp));
  }

  @Bean
  ObservationOrderingPolicy observationOrderingPolicy() {
    return new ObservationOrderingPolicy();
  }

  @Bean
  ObservationIngress observationIngress(
      ObservationTransaction observationTransaction, java.time.Clock applicationClock) {
    return new IngestObservation(observationTransaction, applicationClock);
  }
}
