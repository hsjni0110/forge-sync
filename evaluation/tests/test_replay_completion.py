from __future__ import annotations

from typing import Any

import pytest
from forgesync_evaluation.application.replay_completion import (
    CompletedReplay,
    PollingPolicy,
    ReplayDidNotCompleteError,
    replay_to_completion,
)

SESSION = "00000000-0000-0000-0000-000000000001"
POLLING = PollingPolicy(interval_seconds=5.0, max_polls=3)


class ScriptedReplay:
    def __init__(self, replay_states: list[dict[str, Any]], twin_cursors: list[int]) -> None:
        self._replay_states = iter(replay_states)
        self._twin_cursors = iter(twin_cursors)
        self.started_with: tuple[str, int] | None = None

    def start_replay(self, source_set_id: str, speed_multiplier: int) -> dict[str, Any]:
        self.started_with = (source_set_id, speed_multiplier)
        return {"replaySessionId": SESSION, "status": "RUNNING"}

    def current_replay(self) -> dict[str, Any]:
        return next(self._replay_states)

    def current_twin(self) -> dict[str, Any]:
        return {
            "replayCursor": {"replaySessionId": SESSION, "replaySequence": next(self._twin_cursors)}
        }


def completed(through: int) -> dict[str, Any]:
    return {"status": "COMPLETED", "publicationCursor": {"replaySequence": through}}


def test_replay_completes_only_after_the_twin_applied_the_last_published_sequence() -> None:
    control = ScriptedReplay([{"status": "RUNNING"}, completed(99)], twin_cursors=[97, 99])
    waits: list[float] = []

    result = replay_to_completion(control, "nist-mazak01-20161005", 100, POLLING, waits.append)

    assert result == CompletedReplay(SESSION, 99)
    assert control.started_with == ("nist-mazak01-20161005", 100)
    assert waits == [5.0, 5.0]


def test_failed_replay_is_reported_instead_of_collected() -> None:
    control = ScriptedReplay([{"status": "FAILED", "failure": {"code": "SOURCE"}}], [])

    with pytest.raises(ReplayDidNotCompleteError, match="FAILED"):
        replay_to_completion(control, "nist-mazak01-20161005", 100, POLLING, lambda _: None)


def test_replay_that_outlasts_the_polling_budget_is_reported() -> None:
    control = ScriptedReplay([{"status": "RUNNING"}] * 3, [])

    with pytest.raises(ReplayDidNotCompleteError, match="3 polls"):
        replay_to_completion(control, "nist-mazak01-20161005", 100, POLLING, lambda _: None)
