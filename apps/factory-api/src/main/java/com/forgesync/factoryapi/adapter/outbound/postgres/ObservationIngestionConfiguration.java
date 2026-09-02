package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.forgesync.factoryapi.application.IngestObservation;
import com.forgesync.factoryapi.application.ObservationIngress;
import com.forgesync.factoryapi.application.ObservationTransaction;
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
      JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
    return new PostgresObservationTransaction(jdbcClient, transactionManager);
  }

  @Bean
  ObservationIngress observationIngress(
      ObservationTransaction observationTransaction, Clock ingestionClock) {
    return new IngestObservation(observationTransaction, ingestionClock);
  }
}
