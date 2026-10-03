"""Run one replay to completion and wait until the Twin has applied its last observation."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Protocol

JsonDocument = dict[str, Any]

COMPLETED = "COMPLETED"
FINISHED_STATUSES = frozenset({COMPLETED, "FAILED"})


class ReplayControl(Protocol):
    def start_replay(self, source_set_id: str, speed_multiplier: int) -> JsonDocument: ...

    def current_replay(self) -> JsonDocument: ...

    def current_twin(self) -> JsonDocument: ...


class ReplayDidNotCompleteError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class CompletedReplay:
    replay_session_id: str
    through_replay_sequence: int


@dataclass(frozen=True, slots=True)
class PollingPolicy:
    interval_seconds: float
    max_polls: int


def replay_to_completion(
    control: ReplayControl,
    source_set_id: str,
    speed_multiplier: int,
    polling: PollingPolicy,
    wait: Callable[[float], None],
) -> CompletedReplay:
    session_id = str(control.start_replay(source_set_id, speed_multiplier)["replaySessionId"])
    replay = _poll(control.current_replay, _has_finished, polling, wait, "Replay")
    if replay["status"] != COMPLETED:
        raise ReplayDidNotCompleteError(
            f"Replay {session_id} ended as {replay['status']}: {replay.get('failure')}"
        )
    through = int(replay["publicationCursor"]["replaySequence"])
    # Publication is acknowledged by the broker before the Factory API applies it.
    _poll(
        control.current_twin,
        lambda twin: _has_applied(twin, session_id, through),
        polling,
        wait,
        "Twin",
    )
    return CompletedReplay(session_id, through)


def _poll(
    read: Callable[[], JsonDocument],
    is_settled: Callable[[JsonDocument], bool],
    polling: PollingPolicy,
    wait: Callable[[float], None],
    subject: str,
) -> JsonDocument:
    for _ in range(polling.max_polls):
        document = read()
        if is_settled(document):
            return document
        wait(polling.interval_seconds)
    raise ReplayDidNotCompleteError(f"{subject} did not settle within {polling.max_polls} polls")


def _has_finished(replay: JsonDocument) -> bool:
    return replay["status"] in FINISHED_STATUSES


def _has_applied(twin: JsonDocument, session_id: str, through: int) -> bool:
    cursor = twin.get("replayCursor") or {}
    return bool(
        cursor.get("replaySessionId") == session_id and cursor.get("replaySequence", -1) >= through
    )
