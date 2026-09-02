package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.forgesync.factoryapi.application.IngestObservation;
import com.forgesync.factoryapi.application.ObservationIngress;
import com.forgesync.factoryapi.application.ObservationTransaction;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationOrderingPolicy;
import java.time.Clock;
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
  Clock ingestionClock() {
    return Clock.systemUTC();
  }

  @Bean
  ObservationTransaction observationTransaction(
      JdbcClient jdbcClient,
      PlatformTransactionManager transactionManager,
      ObservationOrderingPolicy observationOrderingPolicy) {
    return new PostgresObservationTransaction(
        jdbcClient, transactionManager, observationOrderingPolicy);
  }

  @Bean
  ObservationOrderingPolicy observationOrderingPolicy() {
    return new ObservationOrderingPolicy();
  }

  @Bean
  ObservationIngress observationIngress(
      ObservationTransaction observationTransaction, Clock ingestionClock) {
    return new IngestObservation(observationTransaction, ingestionClock);
  }
}
