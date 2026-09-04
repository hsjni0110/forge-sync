package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.processanalytics.application.FindMachiningRuns;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.SegmentMachiningRuns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MachiningRunControllerTest {

  private SegmentMachiningRuns segmentMachiningRuns;
  private FindMachiningRuns findMachiningRuns;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    segmentMachiningRuns = mock(SegmentMachiningRuns.class);
    findMachiningRuns = mock(FindMachiningRuns.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new MachiningRunController(segmentMachiningRuns, findMachiningRuns))
            .setControllerAdvice(new MachiningRunErrorHandler())
            .build();
  }

  @Test
  void createsAndReadsVersionedMachiningRuns() throws Exception {
    var result = MachiningRunContractTest.processingResult();
    when(segmentMachiningRuns.segment(any())).thenReturn(result);
    when(findMachiningRuns.find("Mazak01", result.processingRunId()))
        .thenReturn(result.asExisting());

    mockMvc
        .perform(
            post("/api/v1/machines/Mazak01/machining-runs/processing-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "replaySessionId": "00d64db8-967e-41ba-9d09-fdd087710aac",
                      "throughReplaySequence": 45,
                      "segmentationRuleVersion": "1.0.0"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MachiningRunController.MEDIA_TYPE))
        .andExpect(jsonPath("$.machiningRuns[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.machiningRuns[0].provenance.origin").value("DERIVED"));

    mockMvc
        .perform(
            get("/api/v1/machines/Mazak01/machining-runs")
                .param("processingRunId", result.processingRunId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processingRunId").value(result.processingRunId()));
  }

  @Test
  void rejectsInvalidBoundaryInputBeforeCallingTheUseCase() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/machines/Mazak01/machining-runs/processing-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "replaySessionId": "00d64db8-967e-41ba-9d09-fdd087710aac",
                      "throughReplaySequence": -1,
                      "segmentationRuleVersion": "1.0.0"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MACHINING_RUN_REQUEST_INVALID"));
  }

  @Test
  void returnsNotFoundForAnUnknownProcessingRun() throws Exception {
    String processingRunId = "sha256:" + "9".repeat(64);
    when(findMachiningRuns.find("Mazak01", processingRunId))
        .thenThrow(new MachiningRunProcessingNotFoundException(processingRunId));

    mockMvc
        .perform(
            get("/api/v1/machines/Mazak01/machining-runs")
                .param("processingRunId", processingRunId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MACHINING_RUN_INPUT_NOT_FOUND"));
  }
}
