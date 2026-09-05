package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentProcessingResult;
import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentProjectionStore;
import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessment;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresAnomalyAssessmentRepository implements AnomalyAssessmentProjectionStore {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public PostgresAnomalyAssessmentRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public boolean preserve(AnomalyAssessmentProcessingResult result) {
    Boolean created =
        transactionTemplate.execute(
            status -> {
              if (insertProcessingRun(result) == 0) return false;
              result.assessments().forEach(assessment -> insertAssessment(result, assessment));
              return true;
            });
    return Boolean.TRUE.equals(created);
  }

  @Override
  public Optional<AnomalyAssessmentProcessingResult> findAnomalyAssessmentProcessingRun(String id) {
    return jdbcClient
        .sql(
            """
            SELECT assessment_processing_run_id, cycle_feature_processing_run_id,
              machining_run_processing_run_id, machine_id, cycle_feature_version,
              baseline_policy_version, anomaly_assessment_version, input_hash, result_hash,
              created_at
            FROM anomaly_assessment_processing_run
            WHERE assessment_processing_run_id = :id
            """)
        .param("id", id)
        .query(
            (resultSet, rowNumber) ->
                new AnomalyAssessmentProcessingResult(
                    resultSet.getString("assessment_processing_run_id"),
                    resultSet.getString("cycle_feature_processing_run_id"),
                    resultSet.getString("machining_run_processing_run_id"),
                    resultSet.getString("machine_id"),
                    resultSet.getString("cycle_feature_version"),
                    resultSet.getString("baseline_policy_version"),
                    resultSet.getString("anomaly_assessment_version"),
                    resultSet.getString("input_hash"),
                    resultSet.getString("result_hash"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                    false,
                    readAssessments(id)))
        .optional();
  }

  private int insertProcessingRun(AnomalyAssessmentProcessingResult result) {
    return jdbcClient
        .sql(
            """
            INSERT INTO anomaly_assessment_processing_run (
              assessment_processing_run_id, cycle_feature_processing_run_id,
              machining_run_processing_run_id, machine_id, cycle_feature_version,
              baseline_policy_version, anomaly_assessment_version, input_hash, result_hash,
              created_at
            ) VALUES (
              :id, :cycle_id, :machining_id, :machine_id, :feature_version,
              :baseline_version, :assessment_version, :input_hash, :result_hash, :created_at
            ) ON CONFLICT (assessment_processing_run_id) DO NOTHING
            """)
        .param("id", result.assessmentProcessingRunId())
        .param("cycle_id", result.cycleFeatureProcessingRunId())
        .param("machining_id", result.machiningRunProcessingRunId())
        .param("machine_id", result.machineId())
        .param("feature_version", result.cycleFeatureVersion())
        .param("baseline_version", result.baselinePolicyVersion())
        .param("assessment_version", result.anomalyAssessmentVersion())
        .param("input_hash", result.inputHash())
        .param("result_hash", result.resultHash())
        .param("created_at", OffsetDateTime.ofInstant(result.createdAt(), ZoneOffset.UTC))
        .update();
  }

  private void insertAssessment(
      AnomalyAssessmentProcessingResult result, AnomalyAssessment assessment) {
    jdbcClient
        .sql(
            """
            INSERT INTO anomaly_assessment_projection (
              assessment_processing_run_id, assessment_id, machining_run_id,
              target_feature_set_id, machine_id, target_started_at, data_status,
              classification, score, result_hash, assessment_projection
            ) VALUES (
              :processing_id, :assessment_id, :machining_run_id, :feature_set_id,
              :machine_id, :started_at, :data_status, :classification, :score,
              :result_hash, CAST(:projection AS JSONB)
            )
            """)
        .param("processing_id", result.assessmentProcessingRunId())
        .param("assessment_id", assessment.assessmentId())
        .param("machining_run_id", assessment.machiningRunId())
        .param("feature_set_id", assessment.targetFeatureSetId())
        .param("machine_id", result.machineId())
        .param(
            "started_at", OffsetDateTime.ofInstant(assessment.evaluatedStartedAt(), ZoneOffset.UTC))
        .param("data_status", assessment.dataStatus().name())
        .param(
            "classification",
            assessment.classification() == null ? null : assessment.classification().name())
        .param("score", assessment.score())
        .param("result_hash", assessment.resultHash())
        .param("projection", write(assessment))
        .update();
  }

  private List<AnomalyAssessment> readAssessments(String processingRunId) {
    return jdbcClient
        .sql(
            """
            SELECT assessment_projection FROM anomaly_assessment_projection
            WHERE assessment_processing_run_id = :id
            ORDER BY target_started_at, assessment_id
            """)
        .param("id", processingRunId)
        .query(String.class)
        .list()
        .stream()
        .map(this::read)
        .toList();
  }

  private String write(AnomalyAssessment assessment) {
    try {
      return objectMapper.writeValueAsString(assessment);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Anomaly Assessment projection cannot be encoded", exception);
    }
  }

  private AnomalyAssessment read(String document) {
    try {
      return objectMapper
          .readerFor(AnomalyAssessment.class)
          .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .readValue(document);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Anomaly Assessment cannot be decoded", exception);
    }
  }
}
