from __future__ import annotations

from typing import Any

from forgesync_evaluation.application.forgesync_track_a import (
    ProjectionDocuments,
    collect_projection_documents,
)

SESSION = "00000000-0000-0000-0000-000000000001"


class ChainedProjections:
    """Each step answers only when it receives the identifier the previous step produced."""

    def project_state_intervals(self, replay_session_id: str, through: int) -> dict[str, Any]:
        assert (replay_session_id, through) == (SESSION, 10)
        # The interval projection resolves the cursor to its last state observation.
        return {"processingRunId": "sha256:intervals", "throughReplaySequence": 8}

    def segment_machining_runs(self, replay_session_id: str, through: int) -> dict[str, Any]:
        assert (replay_session_id, through) == (SESSION, 8)
        return {"processingRunId": "sha256:runs", "machiningRuns": []}

    def extract_cycle_features(self, runs_id: str) -> dict[str, Any]:
        assert runs_id == "sha256:runs"
        return {"featureProcessingRunId": "sha256:features"}

    def project_utilization(self, intervals_id: str) -> dict[str, Any]:
        assert intervals_id == "sha256:intervals"
        return {"processingRunId": "sha256:utilization"}

    def rank_downtime(self, utilization_id: str) -> dict[str, Any]:
        assert utilization_id == "sha256:utilization"
        return {"processingRunId": "sha256:pareto"}

    def assess_effectiveness(self, utilization_id: str, features_id: str) -> dict[str, Any]:
        assert (utilization_id, features_id) == ("sha256:utilization", "sha256:features")
        return {"processingRunId": "sha256:effectiveness"}

    def find_production_context(self, runs_id: str) -> dict[str, Any]:
        assert runs_id == "sha256:runs"
        return {"machiningRunProcessingRunId": "sha256:runs"}


def test_collection_aligns_every_projection_to_the_interval_resolved_cursor() -> None:
    documents = collect_projection_documents(ChainedProjections(), SESSION, 10)

    assert documents == ProjectionDocuments(
        machining_runs={"processingRunId": "sha256:runs", "machiningRuns": []},
        utilization={"processingRunId": "sha256:utilization"},
        downtime_pareto={"processingRunId": "sha256:pareto"},
        effectiveness={"processingRunId": "sha256:effectiveness"},
        production_context={"machiningRunProcessingRunId": "sha256:runs"},
    )
