package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentService;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureService;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunService;
import com.forgesync.factoryapi.processanalytics.application.ProcessAnomalyAssessmentsCommand;
import com.forgesync.factoryapi.processanalytics.application.ProcessCycleFeaturesCommand;
import com.forgesync.factoryapi.processanalytics.application.SegmentMachiningRunsCommand;
import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessmentPolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleBaselinePolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunSegmentationPolicy;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunStatus;
import com.forgesync.factoryapi.processanalytics.domain.ProcessFactSourcePolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("database-integration")
@EnabledIfEnvironmentVariable(named = "FORGESYNC_DATABASE_INTEGRATION", matches = "1")
class PostgresMachiningRunRepositoryIntegrationTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
  private static JdbcClient jdbcClient;
  private static MachiningRunService service;
  private static CycleFeatureService cycleFeatureService;
  private static AnomalyAssessmentService anomalyAssessmentService;

  @BeforeAll
  static void migrateDatabase() {
    DataSource dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbcClient = JdbcClient.create(dataSource);
    var repository =
        new PostgresMachiningRunRepository(
            jdbcClient, OBJECT_MAPPER, new DataSourceTransactionManager(dataSource));
    service =
        new MachiningRunService(
            repository,
            repository,
            new MachiningRunSegmentationPolicy(),
            new ProcessFactSourcePolicy(),
            Clock.fixed(Instant.parse("2026-09-04T01:02:03Z"), ZoneOffset.UTC));
    var cycleFeatureRepository =
        new PostgresCycleFeatureRepository(
            jdbcClient, OBJECT_MAPPER, new DataSourceTransactionManager(dataSource));
    cycleFeatureService =
        new CycleFeatureService(
            repository,
            cycleFeatureRepository,
            cycleFeatureRepository,
            new CycleFeatureExtractor(),
            Clock.fixed(Instant.parse("2026-09-04T02:02:03Z"), ZoneOffset.UTC));
    var anomalyRepository =
        new PostgresAnomalyAssessmentRepository(
            jdbcClient, OBJECT_MAPPER, new DataSourceTransactionManager(dataSource));
    anomalyAssessmentService =
        new AnomalyAssessmentService(
            cycleFeatureRepository,
            repository,
            anomalyRepository,
            new CycleBaselinePolicy(),
            new AnomalyAssessmentPolicy(),
            Clock.fixed(Instant.parse("2026-09-04T03:02:03Z"), ZoneOffset.UTC));
  }

  @BeforeEach
  void clearDatabase() {
    jdbcClient
        .sql(
            """
            TRUNCATE operational_effectiveness_processing,
              anomaly_assessment_projection, anomaly_assessment_processing_run,
              cycle_feature_projection, cycle_feature_processing_run,
              machining_run_projection, process_analytics_processing_run,
              active_replay_projection, equipment_state_projection, latest_observation_projection,
              equipment_twin_version, canonical_observation_history, ingestion_inbox
            """)
        .update();
  }

  @Test
  void atomicallyPreservesQueriesAndReusesAnomalyAssessmentsWithoutChangingSources() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "PROGRAM", "155");
    insertEvent(3, "EXECUTION", "ACTIVE");
    insertEvent(5, "EXECUTION", "READY");
    var machining = service.segment(command(5));
    var cycle = cycleFeatureService.process(cycleCommand(machining.processingRunId()));

    var first = anomalyAssessmentService.process(anomalyCommand(cycle.featureProcessingRunId()));
    var repeated = anomalyAssessmentService.process(anomalyCommand(cycle.featureProcessingRunId()));
    insertSample(4, 100);
    var lateCycle = cycleFeatureService.process(cycleCommand(machining.processingRunId()));
    var lateAssessment =
        anomalyAssessmentService.process(anomalyCommand(lateCycle.featureProcessingRunId()));
    var queried = anomalyAssessmentService.find("Mazak01", first.assessmentProcessingRunId());

    assertThat(first.assessments()).hasSize(1);
    assertThat(repeated.assessmentProcessingRunId()).isEqualTo(first.assessmentProcessingRunId());
    assertThat(repeated.isCreated()).isFalse();
    assertThat(lateAssessment.assessmentProcessingRunId())
        .isNotEqualTo(first.assessmentProcessingRunId());
    assertThat(queried.assessments()).isEqualTo(first.assessments());
    assertThat(rowCount("anomaly_assessment_processing_run")).isEqualTo(2);
    assertThat(rowCount("anomaly_assessment_projection")).isEqualTo(2);
    assertThat(rowCount("cycle_feature_projection")).isEqualTo(2);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(5);
  }

  @Test
  void rollsBackAnomalyMetadataWhenAssessmentStorageFails() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "PROGRAM", "155");
    insertEvent(3, "EXECUTION", "ACTIVE");
    insertEvent(4, "EXECUTION", "READY");
    var machining = service.segment(command(4));
    var cycle = cycleFeatureService.process(cycleCommand(machining.processingRunId()));
    jdbcClient
        .sql(
            """
            CREATE FUNCTION reject_anomaly_assessment_projection() RETURNS trigger
            LANGUAGE plpgsql AS $$
            BEGIN
              RAISE EXCEPTION 'forced anomaly assessment failure';
            END;
            $$
            """)
        .update();
    jdbcClient
        .sql(
            """
            CREATE TRIGGER reject_anomaly_assessment_projection
            BEFORE INSERT ON anomaly_assessment_projection
            FOR EACH ROW EXECUTE FUNCTION reject_anomaly_assessment_projection()
            """)
        .update();
    try {
      assertThatThrownBy(
              () ->
                  anomalyAssessmentService.process(anomalyCommand(cycle.featureProcessingRunId())))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("forced anomaly assessment failure");
      assertThat(rowCount("anomaly_assessment_processing_run")).isZero();
      assertThat(rowCount("anomaly_assessment_projection")).isZero();
      assertThat(rowCount("cycle_feature_processing_run")).isEqualTo(1);
    } finally {
      jdbcClient
          .sql("DROP TRIGGER reject_anomaly_assessment_projection ON anomaly_assessment_projection")
          .update();
      jdbcClient.sql("DROP FUNCTION reject_anomaly_assessment_projection()").update();
    }
  }

  @Test
  void preservesQueriesAndVersionsCycleFeaturesWithoutChangingSourceRows() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "EXECUTION", "ACTIVE");
    insertSample(3, 100);
    insertEvent(5, "EXECUTION", "READY");
    var machining = service.segment(command(5));

    var first = cycleFeatureService.process(cycleCommand(machining.processingRunId()));
    var repeated = cycleFeatureService.process(cycleCommand(machining.processingRunId()));
    insertSample(4, 200);
    var late = cycleFeatureService.process(cycleCommand(machining.processingRunId()));
    var queried = cycleFeatureService.find("Mazak01", first.featureProcessingRunId());

    assertThat(first.featureSets()).hasSize(1);
    assertThat(first.featureSets().getFirst().cycleFeature().metricFeatures().getFirst().mean())
        .isEqualByComparingTo("100.000000");
    assertThat(repeated.featureProcessingRunId()).isEqualTo(first.featureProcessingRunId());
    assertThat(repeated.isCreated()).isFalse();
    assertThat(late.featureProcessingRunId()).isNotEqualTo(first.featureProcessingRunId());
    assertThat(queried.featureSets()).hasSize(1);
    assertThat(queried.featureSets().getFirst().cycleFeatureSetId())
        .isEqualTo(first.featureSets().getFirst().cycleFeatureSetId());
    assertThat(
            queried.featureSets().getFirst().cycleFeature().metricFeatures().getFirst().maximum())
        .isEqualByComparingTo(
            first.featureSets().getFirst().cycleFeature().metricFeatures().getFirst().maximum());
    assertThat(rowCount("cycle_feature_processing_run")).isEqualTo(2);
    assertThat(rowCount("cycle_feature_projection")).isEqualTo(2);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(5);
  }

  @Test
  void rollsBackCycleFeatureMetadataWhenProjectionStorageFails() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "EXECUTION", "ACTIVE");
    insertSample(3, 100);
    insertEvent(4, "EXECUTION", "READY");
    var machining = service.segment(command(4));
    jdbcClient
        .sql(
            """
            CREATE FUNCTION reject_cycle_feature_projection() RETURNS trigger
            LANGUAGE plpgsql AS $$
            BEGIN
              RAISE EXCEPTION 'forced cycle feature failure';
            END;
            $$
            """)
        .update();
    jdbcClient
        .sql(
            """
            CREATE TRIGGER reject_cycle_feature_projection
            BEFORE INSERT ON cycle_feature_projection
            FOR EACH ROW EXECUTE FUNCTION reject_cycle_feature_projection()
            """)
        .update();
    try {
      assertThatThrownBy(
              () -> cycleFeatureService.process(cycleCommand(machining.processingRunId())))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("forced cycle feature failure");
      assertThat(rowCount("cycle_feature_processing_run")).isZero();
      assertThat(rowCount("cycle_feature_projection")).isZero();
      assertThat(rowCount("machining_run_projection")).isEqualTo(1);
    } finally {
      jdbcClient
          .sql("DROP TRIGGER reject_cycle_feature_projection ON cycle_feature_projection")
          .update();
      jdbcClient.sql("DROP FUNCTION reject_cycle_feature_projection()").update();
    }
  }

  @Test
  void preservesAndReadsAHighConfidenceRunWithoutTreatingPartCountAsAResult() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "PROGRAM", "155");
    insertEvent(3, "EXECUTION", "ACTIVE");
    insertSample(4, 2000);
    insertEvent(5, "PART_COUNT", 12);
    insertEvent(6, "EXECUTION", "READY");

    var result = service.segment(command(6));
    var queried = service.find("Mazak01", result.processingRunId());

    assertThat(result.inputObservationCount()).isEqualTo(5);
    assertThat(result.machiningRuns()).hasSize(1);
    assertThat(result.machiningRuns().getFirst().status()).isEqualTo(MachiningRunStatus.COMPLETED);
    assertThat(result.machiningRuns().getFirst().programName()).isEqualTo("155");
    assertThat(queried.machiningRuns()).isEqualTo(result.machiningRuns());
    assertThat(rowCount("process_analytics_processing_run")).isEqualTo(1);
    assertThat(rowCount("machining_run_projection")).isEqualTo(1);
    assertThat(rowCount("canonical_observation_history")).isEqualTo(6);
  }

  @Test
  void identicalInputIsIdempotentAndLateInputCreatesANewImmutableVersion() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "EXECUTION", "ACTIVE");
    insertEvent(3, "EXECUTION", "READY");
    var first = service.segment(command(3));
    var repeated = service.segment(command(3));
    insertEvent(4, "PROGRAM", "200");
    var reprocessed = service.segment(command(4));

    assertThat(repeated.processingRunId()).isEqualTo(first.processingRunId());
    assertThat(repeated.isCreated()).isFalse();
    assertThat(repeated.machiningRuns()).isEqualTo(first.machiningRuns());
    assertThat(reprocessed.processingRunId()).isNotEqualTo(first.processingRunId());
    assertThat(rowCount("process_analytics_processing_run")).isEqualTo(2);
    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT COUNT(*) FROM machining_run_projection
                    WHERE processing_run_id = :processing_run_id
                    """)
                .param("processing_run_id", first.processingRunId())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void rollsBackTheProcessingRecordWhenRunStorageFails() {
    insertEvent(1, "EXECUTION", "READY");
    insertEvent(2, "EXECUTION", "ACTIVE");
    insertEvent(3, "EXECUTION", "READY");
    jdbcClient
        .sql(
            """
            CREATE FUNCTION reject_machining_run_projection() RETURNS trigger
            LANGUAGE plpgsql AS $$
            BEGIN
              RAISE EXCEPTION 'forced machining run failure';
            END;
            $$
            """)
        .update();
    jdbcClient
        .sql(
            """
            CREATE TRIGGER reject_machining_run_projection
            BEFORE INSERT ON machining_run_projection
            FOR EACH ROW EXECUTE FUNCTION reject_machining_run_projection()
            """)
        .update();
    try {
      assertThatThrownBy(() -> service.segment(command(3)))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("forced machining run failure");
      assertThat(rowCount("process_analytics_processing_run")).isZero();
      assertThat(rowCount("machining_run_projection")).isZero();
      assertThat(rowCount("canonical_observation_history")).isEqualTo(3);
    } finally {
      jdbcClient
          .sql("DROP TRIGGER reject_machining_run_projection ON machining_run_projection")
          .update();
      jdbcClient.sql("DROP FUNCTION reject_machining_run_projection()").update();
    }
  }

  private static SegmentMachiningRunsCommand command(long throughReplaySequence) {
    return new SegmentMachiningRunsCommand(
        "Mazak01", SESSION, throughReplaySequence, MachiningRunSegmentationPolicy.RULE_VERSION);
  }

  private static ProcessCycleFeaturesCommand cycleCommand(String machiningRunProcessingRunId) {
    return new ProcessCycleFeaturesCommand(
        "Mazak01", machiningRunProcessingRunId, CycleFeatureExtractor.FEATURE_VERSION);
  }

  private static ProcessAnomalyAssessmentsCommand anomalyCommand(
      String cycleFeatureProcessingRunId) {
    return new ProcessAnomalyAssessmentsCommand(
        "Mazak01",
        cycleFeatureProcessingRunId,
        CycleBaselinePolicy.POLICY_VERSION,
        AnomalyAssessmentPolicy.POLICY_VERSION);
  }

  private static void insertEvent(long sequence, String eventType, Object value) {
    insertObservation(sequence, "EVENT", eventType, value);
  }

  private static void insertSample(long sequence, int value) {
    insertObservation(sequence, "SAMPLE", "SPINDLE_SPEED", value);
  }

  private static void insertObservation(
      long sequence, String observationKind, String signal, Object value) {
    String sourceEventKey = "source-" + sequence;
    ObjectNode root = OBJECT_MAPPER.createObjectNode();
    root.put("schemaVersion", "2.0.0");
    root.put("eventId", UUID.nameUUIDFromBytes(sourceEventKey.getBytes()).toString());
    root.put("sourceEventKey", sourceEventKey);
    root.put("machineId", "Mazak01");
    root.putObject("subject").put("componentId", "Mazak01-path");
    root.put("observationKind", observationKind);
    root.putObject("source").put("sourceObservedAt", sourceTime(sequence).toString());
    root.putObject("replay")
        .put("replaySessionId", SESSION.toString())
        .put("replaySequence", sequence)
        .put("replayPublishedAt", "2026-09-04T01:00:00Z");
    ObjectNode provenance = root.putObject("provenance");
    provenance
        .putObject("source")
        .put("kind", "REAL")
        .put("provider", "NIST")
        .put("sourceSetId", "nist-mazak01-20161005")
        .put("artifactId", "sha256:" + "a".repeat(64));
    provenance
        .putObject("transformation")
        .put("rawRecordId", sourceEventKey)
        .put("mappingVersion", "2.0.0")
        .put("sourceDataItemId", signal.toLowerCase());
    ObjectNode payload = root.putObject("payload");
    payload.put(observationKind.equals("EVENT") ? "eventType" : "metric", signal);
    payload.put("availability", "AVAILABLE");
    if (value instanceof Integer integer) {
      payload.put("value", integer);
    } else {
      payload.put("value", value.toString());
    }
    if (observationKind.equals("SAMPLE")) {
      payload.put("unit", "REVOLUTION/MINUTE");
    }

    jdbcClient
        .sql(
            """
            INSERT INTO ingestion_inbox (replay_session_id, source_event_key, event_id, ingested_at)
            VALUES (:session, :source_key, :event_id, :ingested_at)
            """)
        .param("session", SESSION)
        .param("source_key", sourceEventKey)
        .param("event_id", UUID.fromString(root.path("eventId").asText()))
        .param("ingested_at", asOffset(Instant.parse("2026-09-04T01:00:00Z")))
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO canonical_observation_history (
              event_id, replay_session_id, source_event_key, schema_version, machine_id,
              component_id, observation_kind, source_observed_at, replay_sequence,
              replay_published_at, ingested_at, artifact_id, raw_record_id, mapping_version,
              source_data_item_id, canonical_envelope
            ) VALUES (
              :event_id, :session, :source_key, '2.0.0', 'Mazak01', 'Mazak01-path',
              :observation_kind, :source_observed_at, :replay_sequence, :replay_published_at,
              :ingested_at, :artifact_id, :raw_record_id, '2.0.0', :source_data_item_id,
              CAST(:canonical_envelope AS JSONB)
            )
            """)
        .param("event_id", UUID.fromString(root.path("eventId").asText()))
        .param("session", SESSION)
        .param("source_key", sourceEventKey)
        .param("observation_kind", observationKind)
        .param("source_observed_at", asOffset(sourceTime(sequence)))
        .param("replay_sequence", sequence)
        .param("replay_published_at", asOffset(Instant.parse("2026-09-04T01:00:00Z")))
        .param("ingested_at", asOffset(Instant.parse("2026-09-04T01:00:01Z")))
        .param("artifact_id", "sha256:" + "a".repeat(64))
        .param("raw_record_id", sourceEventKey)
        .param("source_data_item_id", signal.toLowerCase())
        .param("canonical_envelope", root.toString())
        .update();
  }

  private static Instant sourceTime(long sequence) {
    return Instant.parse("2016-10-05T09:00:00Z").plusSeconds(sequence);
  }

  private static OffsetDateTime asOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static long rowCount(String table) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
  }

  private static DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(
        System.getenv()
            .getOrDefault("FORGESYNC_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15432/forgesync"));
    dataSource.setUsername(
        System.getenv().getOrDefault("FORGESYNC_DATABASE_USERNAME", "forgesync"));
    dataSource.setPassword(
        System.getenv().getOrDefault("FORGESYNC_DATABASE_PASSWORD", "forgesync-test"));
    return dataSource;
  }
}
