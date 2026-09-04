package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.processanalytics.application.FindCycleFeatures;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.ProcessCycleFeatures;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureUnitMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CycleFeatureControllerTest {
  private ProcessCycleFeatures processCycleFeatures;
  private FindCycleFeatures findCycleFeatures;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    processCycleFeatures = mock(ProcessCycleFeatures.class);
    findCycleFeatures = mock(FindCycleFeatures.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new CycleFeatureController(processCycleFeatures, findCycleFeatures))
            .setControllerAdvice(new CycleFeatureErrorHandler())
            .build();
  }

  @Test
  void createsReusesAndReadsVersionedCycleFeatures() throws Exception {
    var created = CycleFeatureContractTest.processingResult(true);
    var existing = created.asExisting();
    when(processCycleFeatures.process(any())).thenReturn(created, existing);
    when(findCycleFeatures.find("Mazak01", created.featureProcessingRunId())).thenReturn(existing);
    String body =
        """
        {"machiningRunProcessingRunId":"sha256:%s","cycleFeatureVersion":"1.0.0"}
        """
            .formatted("2".repeat(64));

    mockMvc
        .perform(postRequest(body))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(CycleFeatureController.MEDIA_TYPE))
        .andExpect(jsonPath("$.eligibleRunCount").value(1))
        .andExpect(jsonPath("$.featureSets[0].provenance.origin").value("DERIVED"));
    mockMvc.perform(postRequest(body)).andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/machines/Mazak01/cycle-features")
                .param("featureProcessingRunId", created.featureProcessingRunId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.featureProcessingRunId").value(created.featureProcessingRunId()));
  }

  @Test
  void rejectsInvalidVersionAndTranslatesUnitMismatch() throws Exception {
    mockMvc
        .perform(
            postRequest(
                "{\"machiningRunProcessingRunId\":\"sha256:"
                    + "2".repeat(64)
                    + "\",\"cycleFeatureVersion\":\"2.0.0\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CYCLE_FEATURE_REQUEST_INVALID"));

    when(processCycleFeatures.process(any()))
        .thenThrow(new CycleFeatureUnitMismatchException("SPINDLE_SPEED|spindle|rpm"));
    mockMvc
        .perform(
            postRequest(
                "{\"machiningRunProcessingRunId\":\"sha256:"
                    + "2".repeat(64)
                    + "\",\"cycleFeatureVersion\":\"1.0.0\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CYCLE_FEATURE_UNIT_MISMATCH"));
  }

  @Test
  void returnsNotFoundWhenTheMachiningRunProcessingSourceDoesNotExist() throws Exception {
    String sourceProcessingRunId = "sha256:" + "9".repeat(64);
    when(processCycleFeatures.process(any()))
        .thenThrow(new MachiningRunProcessingNotFoundException(sourceProcessingRunId));

    mockMvc
        .perform(
            postRequest(
                "{\"machiningRunProcessingRunId\":\""
                    + sourceProcessingRunId
                    + "\",\"cycleFeatureVersion\":\"1.0.0\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CYCLE_FEATURE_INPUT_NOT_FOUND"));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      postRequest(String body) {
    return post("/api/v1/machines/Mazak01/cycle-features/processing-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }
}
