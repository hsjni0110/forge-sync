package com.forgesync.factoryapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "forgesync.ingestion.enabled=false",
      "forgesync.twin.query.enabled=false",
      "forgesync.replay.enabled=false",
      "forgesync.process-analytics.enabled=false",
      "forgesync.equipment-state-intervals.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
@AutoConfigureMockMvc
class FactoryApiSmokeTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void exposesApplicationHealthWithoutDomainReadinessClaims() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
