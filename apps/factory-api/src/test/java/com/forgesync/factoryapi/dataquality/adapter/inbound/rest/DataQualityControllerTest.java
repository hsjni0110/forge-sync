package com.forgesync.factoryapi.dataquality.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence;
import com.forgesync.factoryapi.dataquality.application.DataQualityReport;
import com.forgesync.factoryapi.dataquality.application.DataQualityReportAssembler;
import com.forgesync.factoryapi.dataquality.application.GetDataQualityReport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataQualityControllerTest {
  private final DataQualityReport report =
      new DataQualityReportAssembler()
          .assembleSource(
              new ClasspathDataQualityEvidence().load(), Instant.parse("2026-09-13T09:00:00Z"));
  private final GetDataQualityReport query =
      new GetDataQualityReport() {
        @Override
        public DataQualityReport getSource(String machineId) {
          return report;
        }

        @Override
        public DataQualityReport getScoped(
            String machineId, UUID replaySessionId, long throughReplaySequence) {
          return report;
        }
      };

  @Test
  void returnsTheVersionedSourceReportWithoutReplayScope() throws Exception {
    MockMvcBuilders.standaloneSetup(new DataQualityController(query))
        .build()
        .perform(
            get("/api/v1/machines/Mazak01/data-quality").accept(DataQualityController.MEDIA_TYPE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(DataQualityController.MEDIA_TYPE));
  }

  @Test
  void rejectsAPartialReplayScope() throws Exception {
    MockMvcBuilders.standaloneSetup(new DataQualityController(query))
        .build()
        .perform(
            get("/api/v1/machines/Mazak01/data-quality").queryParam("throughReplaySequence", "42"))
        .andExpect(status().isBadRequest());
  }
}
