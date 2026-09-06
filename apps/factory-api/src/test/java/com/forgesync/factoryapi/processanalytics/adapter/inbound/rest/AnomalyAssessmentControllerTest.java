package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.FindAnomalyAssessments;
import com.forgesync.factoryapi.processanalytics.application.ProcessAnomalyAssessments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnomalyAssessmentControllerTest {
  private ProcessAnomalyAssessments processor;
  private FindAnomalyAssessments finder;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    processor = mock(ProcessAnomalyAssessments.class);
    finder = mock(FindAnomalyAssessments.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AnomalyAssessmentController(processor, finder))
            .setControllerAdvice(new AnomalyAssessmentErrorHandler())
            .build();
  }

  @Test
  void createsReusesAndReadsAssessments() throws Exception {
    var created = AnomalyAssessmentContractTest.processingResult(true);
    when(processor.process(any())).thenReturn(created, created.asExisting());
    when(finder.find("Mazak01", created.assessmentProcessingRunId()))
        .thenReturn(created.asExisting());

    mockMvc
        .perform(postRequest("1.0.0"))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(AnomalyAssessmentController.MEDIA_TYPE))
        .andExpect(jsonPath("$.assessments[0].dataStatus").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.assessments[0].lineage.inputCycleFeature.origin").value("DERIVED"))
        .andExpect(jsonPath("$.assessments[0].lineage.sources[0].provider").value("NIST"));
    mockMvc.perform(postRequest("1.0.0")).andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/machines/Mazak01/anomaly-assessments")
                .param("assessmentProcessingRunId", created.assessmentProcessingRunId()))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsInvalidVersionAndReturnsSourceNotFound() throws Exception {
    mockMvc
        .perform(postRequest("2.0.0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ANOMALY_ASSESSMENT_REQUEST_INVALID"));
    when(processor.process(any()))
        .thenThrow(new CycleFeatureProcessingNotFoundException("sha256:" + "2".repeat(64)));
    mockMvc
        .perform(postRequest("1.0.0"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANOMALY_ASSESSMENT_INPUT_NOT_FOUND"));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      postRequest(String baselineVersion) {
    return post("/api/v1/machines/Mazak01/anomaly-assessments/processing-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"cycleFeatureProcessingRunId":"sha256:%s","baselinePolicyVersion":"%s",
             "anomalyAssessmentVersion":"3.0.0"}
            """
                .formatted("2".repeat(64), baselineVersion));
  }
}
