package com.forgesync.factoryapi.dataquality.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.dataquality.adapter.outbound.postgres.PostgresDataQualityQuery;
import com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence;
import com.forgesync.factoryapi.dataquality.application.DataQualityService;
import com.forgesync.factoryapi.dataquality.application.GetDataQualityReport;
import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.twin.query.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DataQualityConfiguration {
  @Bean
  GetDataQualityReport getDataQualityReport(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      GetOperationalTwinSnapshot twinQuery,
      Clock applicationClock) {
    var source = new ClasspathDataQualityEvidence(objectMapper).load();
    return new DataQualityService(
        source, new PostgresDataQualityQuery(jdbcClient), twinQuery, applicationClock);
  }
}
