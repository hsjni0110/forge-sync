package com.forgesync.factoryapi.alarm.adapter;

import com.forgesync.factoryapi.alarm.adapter.outbound.postgres.PostgresAlarmRepository;
import com.forgesync.factoryapi.alarm.application.AlarmService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AlarmConfiguration {
  @Bean
  PostgresAlarmRepository alarmRepository(
      JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
    return new PostgresAlarmRepository(jdbcClient, new TransactionTemplate(transactionManager));
  }

  @Bean
  AlarmService alarmService(PostgresAlarmRepository repository, Clock applicationClock) {
    return new AlarmService(repository, applicationClock);
  }
}
