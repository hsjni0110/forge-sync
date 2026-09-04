package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.FieldProvenance;
import com.forgesync.factoryapi.equipmenttwin.application.LoadedTwinProjection;
import com.forgesync.factoryapi.equipmenttwin.application.ProjectedTwinObservation;
import com.forgesync.factoryapi.equipmenttwin.application.ReplayCursor;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionReader;
import com.forgesync.factoryapi.equipmenttwin.application.TwinSnapshotUnavailableException;
import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservationKind;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresTwinProjectionReader implements TwinProjectionReader {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public PostgresTwinProjectionReader(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    transactionTemplate.setReadOnly(true);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @Override
  public Optional<LoadedTwinProjection> findByMachineId(String machineId) {
    try {
      return transactionTemplate.execute(status -> loadWithinTransaction(machineId));
    } catch (DataAccessException exception) {
      throw new TwinSnapshotUnavailableException("Twin projection query failed", exception);
    }
  }

  private Optional<LoadedTwinProjection> loadWithinTransaction(String machineId) {
    Optional<ProjectionHeader> header = findHeader(machineId);
    if (header.isEmpty()) {
      return Optional.empty();
    }
    ProjectionHeader found = header.get();
    if (found.stateVersion() == null) {
      throw new TwinSnapshotUnavailableException("Equipment State projection is unavailable");
    }
    return Optional.of(
        new LoadedTwinProjection(
            machineId,
            new TwinVersion(found.twinVersion()),
            found.projectedAt().toInstant(),
            new ReplayCursor(
                found.replaySessionId(),
                found.replaySequence(),
                found.sourceObservedAt().toInstant(),
                found.replayPublishedAt().toInstant(),
                new TwinVersion(found.twinVersion())),
            new EquipmentState(
                ConnectivityState.valueOf(found.connectivity()),
                ExecutionState.valueOf(found.execution()),
                HealthState.valueOf(found.health())),
            new TwinVersion(found.stateVersion()),
            findObservations(machineId)));
  }

  private Optional<ProjectionHeader> findHeader(String machineId) {
    return jdbcClient
        .sql(
            """
            SELECT version.twin_version, version.projected_at, version.replay_session_id,
                   version.replay_sequence, version.source_observed_at,
                   version.replay_published_at,
                   state.connectivity_state, state.execution_state, state.health_state,
                   state.twin_version AS state_version
            FROM equipment_twin_version version
            LEFT JOIN equipment_state_projection state ON state.machine_id = version.machine_id
            WHERE version.machine_id = :machine_id
            """)
        .param("machine_id", machineId)
        .query(
            (resultSet, rowNumber) ->
                new ProjectionHeader(
                    resultSet.getLong("twin_version"),
                    resultSet.getObject("projected_at", OffsetDateTime.class),
                    resultSet.getObject("replay_session_id", UUID.class),
                    resultSet.getLong("replay_sequence"),
                    resultSet.getObject("source_observed_at", OffsetDateTime.class),
                    resultSet.getObject("replay_published_at", OffsetDateTime.class),
                    resultSet.getString("connectivity_state"),
                    resultSet.getString("execution_state"),
                    resultSet.getString("health_state"),
                    resultSet.getObject("state_version", Long.class)))
        .optional();
  }

  private List<ProjectedTwinObservation> findObservations(String machineId) {
    return jdbcClient
        .sql(
            """
            SELECT canonical_envelope::text, projected_at, twin_version
            FROM latest_observation_projection
            WHERE machine_id = :machine_id
            """)
        .param("machine_id", machineId)
        .query(
            (resultSet, rowNumber) ->
                readObservation(
                    resultSet.getString("canonical_envelope"),
                    resultSet.getObject("projected_at", OffsetDateTime.class),
                    resultSet.getLong("twin_version")))
        .list();
  }

  private ProjectedTwinObservation readObservation(
      String canonicalEnvelope, OffsetDateTime projectedAt, long twinVersion) {
    try {
      JsonNode document = objectMapper.readTree(canonicalEnvelope);
      JsonNode payload = document.path("payload");
      StateObservationKind kind =
          StateObservationKind.valueOf(document.path("observationKind").asText());
      JsonNode provenance = document.path("provenance");
      JsonNode source = provenance.path("source");
      JsonNode transformation = provenance.path("transformation");
      ObservationAvailability availability = availability(kind, payload);
      JsonNode value = value(kind, payload, availability);
      return new ProjectedTwinObservation(
          kind,
          semanticType(kind, payload),
          availability,
          value != null && value.isNumber() && !value.isIntegralNumber()
              ? value.decimalValue()
              : numericSample(kind, value),
          value != null && value.isTextual() ? value.textValue() : conditionLevel(kind, payload),
          value != null && value.isIntegralNumber() ? value.longValue() : null,
          textOrNull(payload, "unit"),
          document.path("subject").path("componentId").asText(),
          java.time.Instant.parse(document.path("source").path("sourceObservedAt").asText()),
          projectedAt.toInstant(),
          new TwinVersion(twinVersion),
          new FieldProvenance(
              source.path("kind").asText(),
              source.path("provider").asText(),
              source.path("sourceSetId").asText(),
              source.path("artifactId").asText(),
              transformation.path("rawRecordId").asText(),
              transformation.path("mappingVersion").asText(),
              transformation.path("sourceDataItemId").asText()),
          textOrNull(payload, "nativeCode"),
          textOrNull(payload, "nativeSeverity"),
          textOrNull(payload, "qualifier"),
          textOrNull(payload, "message"));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new TwinSnapshotUnavailableException(
          "Validated Canonical Observation cannot be read", exception);
    }
  }

  private static BigDecimal numericSample(StateObservationKind kind, JsonNode value) {
    return kind == StateObservationKind.SAMPLE && value != null && value.isNumber()
        ? value.decimalValue()
        : null;
  }

  private static ObservationAvailability availability(StateObservationKind kind, JsonNode payload) {
    if (kind == StateObservationKind.CONDITION) {
      return payload.path("level").asText().equals("UNAVAILABLE")
          ? ObservationAvailability.UNAVAILABLE
          : ObservationAvailability.AVAILABLE;
    }
    return ObservationAvailability.valueOf(payload.path("availability").asText());
  }

  private static JsonNode value(
      StateObservationKind kind, JsonNode payload, ObservationAvailability availability) {
    if (kind == StateObservationKind.CONDITION
        || availability == ObservationAvailability.UNAVAILABLE) {
      return null;
    }
    return payload.path("value");
  }

  private static String conditionLevel(StateObservationKind kind, JsonNode payload) {
    return kind == StateObservationKind.CONDITION ? payload.path("level").asText() : null;
  }

  private static String semanticType(StateObservationKind kind, JsonNode payload) {
    return switch (kind) {
      case SAMPLE -> payload.path("metric").asText();
      case EVENT -> payload.path("eventType").asText();
      case CONDITION -> payload.path("conditionType").asText();
    };
  }

  private static String textOrNull(JsonNode document, String fieldName) {
    JsonNode value = document.get(fieldName);
    return value == null || value.isNull() ? null : value.asText();
  }

  private record ProjectionHeader(
      long twinVersion,
      OffsetDateTime projectedAt,
      UUID replaySessionId,
      long replaySequence,
      OffsetDateTime sourceObservedAt,
      OffsetDateTime replayPublishedAt,
      String connectivity,
      String execution,
      String health,
      Long stateVersion) {}
}
