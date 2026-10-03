"""Collect the ForgeSync projection documents that answer the Track A questions."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

JsonDocument = dict[str, Any]


class ProjectionSource(Protocol):
    def project_state_intervals(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument: ...

    def segment_machining_runs(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument: ...

    def extract_cycle_features(self, machining_run_processing_run_id: str) -> JsonDocument: ...

    def project_utilization(self, interval_processing_run_id: str) -> JsonDocument: ...

    def rank_downtime(self, utilization_processing_run_id: str) -> JsonDocument: ...

    def assess_effectiveness(
        self, utilization_processing_run_id: str, cycle_feature_processing_run_id: str
    ) -> JsonDocument: ...

    def find_production_context(self, machining_run_processing_run_id: str) -> JsonDocument: ...


@dataclass(frozen=True, slots=True)
class ProjectionDocuments:
    machining_runs: JsonDocument
    utilization: JsonDocument
    downtime_pareto: JsonDocument
    effectiveness: JsonDocument
    production_context: JsonDocument


def collect_projection_documents(
    source: ProjectionSource, replay_session_id: str, through_replay_sequence: int
) -> ProjectionDocuments:
    intervals = source.project_state_intervals(replay_session_id, through_replay_sequence)
    # Intervals resolve the cursor to their last state observation, while machining runs keep
    # the requested one; effectiveness rejects mismatched cursors, so every step uses the
    # resolved cursor. See the evaluation report for this observed ForgeSync behaviour.
    resolved_through = int(intervals["throughReplaySequence"])
    machining_runs = source.segment_machining_runs(replay_session_id, resolved_through)
    cycle_features = source.extract_cycle_features(machining_runs["processingRunId"])
    utilization = source.project_utilization(intervals["processingRunId"])
    return ProjectionDocuments(
        machining_runs=machining_runs,
        utilization=utilization,
        downtime_pareto=source.rank_downtime(utilization["processingRunId"]),
        effectiveness=source.assess_effectiveness(
            utilization["processingRunId"], cycle_features["featureProcessingRunId"]
        ),
        production_context=source.find_production_context(machining_runs["processingRunId"]),
    )
