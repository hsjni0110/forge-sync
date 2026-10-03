from __future__ import annotations

from typing import Any

from forgesync_evaluation.application.forgesync_track_b import (
    DeliveryDocuments,
    collect_delivery_documents,
)

SESSION = "00000000-0000-0000-0000-000000000001"


class ResolvingSource:
    def project_state_intervals(self, replay_session_id: str, through: int) -> dict[str, Any]:
        assert (replay_session_id, through) == (SESSION, 10)
        return {"throughReplaySequence": 8, "intervals": []}

    def segment_machining_runs(self, replay_session_id: str, through: int) -> dict[str, Any]:
        assert (replay_session_id, through) == (SESSION, 8)
        return {"machiningRuns": []}

    def current_twin(self) -> dict[str, Any]:
        return {"replayCursor": {"replaySequence": 10}}

    def find_alarms(self, replay_session_id: str, through: int) -> dict[str, Any]:
        assert (replay_session_id, through) == (SESSION, 8)
        return {"alarms": []}


def test_delivery_documents_share_the_interval_resolved_cursor() -> None:
    documents = collect_delivery_documents(ResolvingSource(), SESSION, 10)

    assert documents == DeliveryDocuments(
        twin={"replayCursor": {"replaySequence": 10}},
        intervals={"throughReplaySequence": 8, "intervals": []},
        machining_runs={"machiningRuns": []},
        alarms={"alarms": []},
    )
