package com.forgesync.factoryapi.dataquality.adapter.outbound.postgres;

import com.forgesync.factoryapi.dataquality.application.DataQualityQuery;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.DerivedProcessSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.FeatureCoverageSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.RuntimeSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.SegmentationSnapshot;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresDataQualityQuery implements DataQualityQuery {
  private final JdbcClient jdbcClient;

  public PostgresDataQualityQuery(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public Optional<RuntimeSnapshot> findRuntime(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    return jdbcClient
        .sql(
            """
            SELECT SUM(received_count) received_count, SUM(accepted_count) accepted_count,
              SUM(duplicate_count) duplicate_count, SUM(out_of_order_count) out_of_order_count,
              MIN(first_ingested_at) first_ingested_at, MAX(last_ingested_at) last_ingested_at
            FROM runtime_data_quality_observation
            WHERE machine_id = :machine_id AND replay_session_id = :replay_session_id
              AND replay_sequence <= :through_replay_sequence
            HAVING COUNT(*) > 0
            """)
        .param("machine_id", machineId)
        .param("replay_session_id", replaySessionId)
        .param("through_replay_sequence", throughReplaySequence)
        .query(
            (resultSet, rowNumber) ->
                new RuntimeSnapshot(
                    resultSet.getLong("received_count"),
                    resultSet.getLong("accepted_count"),
                    resultSet.getLong("duplicate_count"),
                    resultSet.getLong("out_of_order_count"),
                    resultSet.getObject("first_ingested_at", OffsetDateTime.class).toInstant(),
                    resultSet.getObject("last_ingested_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  @Override
  public Optional<DerivedProcessSnapshot> findDerivedProcess(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    Optional<SegmentationSnapshot> segmentation =
        jdbcClient
            .sql(
                """
                SELECT processing.processing_run_id, processing.input_observation_count,
                  COUNT(runs.machining_run_id) result_count
                FROM process_analytics_processing_run processing
                LEFT JOIN machining_run_projection runs
                  ON runs.processing_run_id = processing.processing_run_id
                WHERE processing.machine_id = :machine_id
                  AND processing.replay_session_id = :replay_session_id
                  AND processing.through_replay_sequence <= :through_replay_sequence
                GROUP BY processing.processing_run_id, processing.input_observation_count,
                  processing.through_replay_sequence, processing.created_at
                ORDER BY processing.through_replay_sequence DESC, processing.created_at DESC
                LIMIT 1
                """)
            .param("machine_id", machineId)
            .param("replay_session_id", replaySessionId)
            .param("through_replay_sequence", throughReplaySequence)
            .query(
                (resultSet, rowNumber) ->
                    new SegmentationSnapshot(
                        resultSet.getString("processing_run_id"),
                        resultSet.getInt("input_observation_count"),
                        resultSet.getInt("result_count")))
            .optional();
    FeatureCoverageSnapshot featureCoverage =
        segmentation.flatMap(this::findFeatureCoverage).orElse(null);
    if (segmentation.isEmpty() && featureCoverage == null) return Optional.empty();
    return Optional.of(new DerivedProcessSnapshot(segmentation.orElse(null), featureCoverage));
  }

  private Optional<FeatureCoverageSnapshot> findFeatureCoverage(SegmentationSnapshot segmentation) {
    java.util.List<Map<String, Object>> rows =
        jdbcClient
            .sql(
                """
            SELECT processing.feature_processing_run_id, processing.eligible_run_count,
              projection.feature_status, COUNT(projection.cycle_feature_set_id) status_count
            FROM cycle_feature_processing_run processing
            LEFT JOIN cycle_feature_projection projection
              ON projection.feature_processing_run_id = processing.feature_processing_run_id
            WHERE processing.feature_processing_run_id = (
              SELECT latest.feature_processing_run_id
              FROM cycle_feature_processing_run latest
              WHERE latest.machining_run_processing_run_id = :processing_run_id
              ORDER BY latest.created_at DESC, latest.feature_processing_run_id
              LIMIT 1
            )
            GROUP BY processing.feature_processing_run_id, processing.eligible_run_count,
              projection.feature_status
            """)
            .param("processing_run_id", segmentation.processingRunId())
            .query()
            .listOfRows();
    return rows.isEmpty() ? Optional.empty() : Optional.of(featureCoverage(rows));
  }

  private static FeatureCoverageSnapshot featureCoverage(java.util.List<Map<String, Object>> rows) {
    Map<String, Object> first = rows.getFirst();
    return new FeatureCoverageSnapshot(
        first.get("feature_processing_run_id").toString(),
        ((Number) first.get("eligible_run_count")).intValue(),
        count(rows, "AVAILABLE"),
        count(rows, "PARTIAL"),
        count(rows, "MISSING"),
        count(rows, "EMPTY_WINDOW"));
  }

  private static int count(java.util.List<Map<String, Object>> rows, String status) {
    return rows.stream()
        .filter(row -> status.equals(row.get("feature_status")))
        .mapToInt(row -> ((Number) row.get("status_count")).intValue())
        .sum();
  }
}
