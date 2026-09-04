package com.forgesync.factoryapi.replay.adapter.inbound.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.replay.application.ControlReplay;
import com.forgesync.factoryapi.replay.application.ReplaySessionState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReplayControllerTest {
  private ControlReplay controlReplay;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    controlReplay = mock(ControlReplay.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ReplayController(controlReplay))
            .setControllerAdvice(new ReplayErrorHandler())
            .build();
  }

  @Test
  void startsAnAllowlistedReplayThroughTheVersionedBoundary() throws Exception {
    when(controlReplay.start("Mazak01", "nist-mazak01-20161005", 10)).thenReturn(runningState());

    mockMvc
        .perform(
            post("/api/v1/replay-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"machineId":"Mazak01","sourceSetId":"nist-mazak01-20161005",
                     "speedMultiplier":10}
                    """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(ReplayController.REPLAY_MEDIA_TYPE))
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.speedMultiplier").value(10))
        .andExpect(jsonPath("$.publicationCursor").doesNotExist())
        .andExpect(jsonPath("$.failure").doesNotExist());
  }

  @Test
  void rejectsUnsupportedSpeedWithoutExposingAnInternalFailure() throws Exception {
    when(controlReplay.start("Mazak01", "nist-mazak01-20161005", 2))
        .thenThrow(new IllegalArgumentException("internal detail"));

    mockMvc
        .perform(
            post("/api/v1/replay-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"machineId":"Mazak01","sourceSetId":"nist-mazak01-20161005",
                     "speedMultiplier":2}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("REPLAY_COMMAND_INVALID"));
  }

  private static ReplaySessionState runningState() {
    Instant startsAt = Instant.parse("2016-10-05T05:27:55Z");
    return new ReplaySessionState(
        "1.0.0",
        UUID.fromString("10000000-0000-4000-8000-000000000001"),
        "Mazak01",
        "nist-mazak01-20161005",
        "RUNNING",
        10,
        1,
        new ReplaySessionState.SourceRange(startsAt, startsAt.plusSeconds(10)),
        null,
        null);
  }
}
