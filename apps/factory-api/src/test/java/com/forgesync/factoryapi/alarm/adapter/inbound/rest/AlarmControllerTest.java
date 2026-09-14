package com.forgesync.factoryapi.alarm.adapter.inbound.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgesync.factoryapi.alarm.application.AcknowledgeAlarm;
import com.forgesync.factoryapi.alarm.application.FindAlarms;
import com.forgesync.factoryapi.alarm.domain.Alarm;
import com.forgesync.factoryapi.alarm.domain.AlarmCandidate;
import com.forgesync.factoryapi.alarm.domain.AlarmSeverity;
import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlarmControllerTest {
  private static final UUID SESSION = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID ALARM_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private FindAlarms findAlarms;
  private AcknowledgeAlarm acknowledgeAlarm;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    findAlarms = mock(FindAlarms.class);
    acknowledgeAlarm = mock(AcknowledgeAlarm.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AlarmController(findAlarms, acknowledgeAlarm))
            .setControllerAdvice(new AlarmErrorHandler())
            .build();
  }

  @Test
  void exposesConditionDerivedAlarmIdentityAtTheReplayCursor() throws Exception {
    when(findAlarms.find("Mazak01", SESSION, 42)).thenReturn(List.of(openAlarm()));

    mockMvc
        .perform(
            get("/api/v1/machines/Mazak01/alarms")
                .queryParam("replaySessionId", SESSION.toString())
                .queryParam("throughReplaySequence", "42"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(AlarmController.MEDIA_TYPE))
        .andExpect(jsonPath("$.alarms[0].alarmId").value(ALARM_ID.toString()))
        .andExpect(jsonPath("$.alarms[0].source").value("EQUIPMENT_CONDITION"))
        .andExpect(jsonPath("$.alarms[0].severity").value("WARNING"));
  }

  @Test
  void acknowledgesWithOperatorNameAndReturnsTheSameAlarmIdentity() throws Exception {
    Alarm acknowledged = openAlarm().acknowledge("김 작업자", Instant.parse("2026-09-13T01:00:00Z"));
    when(acknowledgeAlarm.acknowledge(ALARM_ID, 0, "김 작업자")).thenReturn(acknowledged);

    mockMvc
        .perform(
            post("/api/v1/alarms/{alarmId}/acknowledge", ALARM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"operatorName\":\"김 작업자\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(AlarmController.MEDIA_TYPE))
        .andExpect(jsonPath("$.alarmId").value(ALARM_ID.toString()))
        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
        .andExpect(jsonPath("$.acknowledgedBy").value("김 작업자"));
  }

  private static Alarm openAlarm() {
    var condition =
        new ConditionObservation(
            "Mazak01",
            SESSION,
            42,
            Instant.parse("2016-10-05T09:01:00Z"),
            "warning-42",
            "Mazak01-controller_2",
            "Mazak01-controller",
            "LOGIC_PROGRAM",
            "WARNING",
            "345",
            "ERROR(DOOR OPEN)");
    return Alarm.open(ALARM_ID, new AlarmCandidate(condition, AlarmSeverity.WARNING, "1.0.0"));
  }
}
