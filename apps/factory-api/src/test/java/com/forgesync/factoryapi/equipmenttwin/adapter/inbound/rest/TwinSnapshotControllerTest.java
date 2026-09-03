package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.MachineTwinNotFoundException;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.TwinConsistencyState;
import com.forgesync.factoryapi.equipmenttwin.application.TwinSnapshotUnavailableException;
import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TwinSnapshotControllerTest {

  private GetOperationalTwinSnapshot getOperationalTwinSnapshot;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    getOperationalTwinSnapshot = mock(GetOperationalTwinSnapshot.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TwinSnapshotController(getOperationalTwinSnapshot))
            .setControllerAdvice(new TwinSnapshotErrorHandler())
            .build();
  }

  @Test
  void exposesHumanReadableVersionedSnapshot() throws Exception {
    when(getOperationalTwinSnapshot.getSnapshot("Mazak01")).thenReturn(snapshot());

    mockMvc
        .perform(get("/api/v1/machines/Mazak01/twin"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(TwinSnapshotController.TWIN_MEDIA_TYPE))
        .andExpect(jsonPath("$.schemaVersion").value("1.1.0"))
        .andExpect(jsonPath("$.machine.machineId").value("Mazak01"))
        .andExpect(jsonPath("$.consistency.twinVersion").value(4))
        .andExpect(jsonPath("$.state.freshness.value").value("FRESH"))
        .andExpect(jsonPath("$.metrics.spindleSpeeds").isArray())
        .andExpect(jsonPath("$.production").isMap())
        .andExpect(jsonPath("$.alarms").isArray());
  }

  @Test
  void rejectsInvalidMachineIdentityBeforeTheUseCase() throws Exception {
    mockMvc
        .perform(get("/api/v1/machines/invalid%20machine/twin"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_MACHINE_ID"));
  }

  @Test
  void distinguishesNotFoundFromTemporarilyUnavailable() throws Exception {
    when(getOperationalTwinSnapshot.getSnapshot("missing"))
        .thenThrow(new MachineTwinNotFoundException("missing"));
    when(getOperationalTwinSnapshot.getSnapshot("broken"))
        .thenThrow(new TwinSnapshotUnavailableException("database secret detail"));

    mockMvc
        .perform(get("/api/v1/machines/missing/twin"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MACHINE_TWIN_NOT_FOUND"));
    mockMvc
        .perform(get("/api/v1/machines/broken/twin"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("TWIN_SNAPSHOT_UNAVAILABLE"))
        .andExpect(
            jsonPath("$.detail")
                .value("The authoritative Twin snapshot is temporarily unavailable"));
  }

  private static OperationalTwinSnapshot snapshot() {
    Instant projectedAt = Instant.parse("2026-09-02T01:02:04Z");
    return new OperationalTwinSnapshot(
        "Mazak01",
        new TwinVersion(4),
        projectedAt,
        TwinConsistencyState.PARTIAL,
        List.of("metrics.spindleSpeeds", "state.health", "metrics.toolNumber", "metrics.program"),
        ConnectivityState.ONLINE,
        ExecutionState.ACTIVE,
        HealthState.UNKNOWN,
        FreshnessState.FRESH,
        projectedAt.plusSeconds(1),
        Duration.ofSeconds(1),
        2_000,
        10_000,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        List.of());
  }
}
