from __future__ import annotations

import json
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import pytest
from forgesync_edge.replay.adapter.outbound import JsonReplayEnvelopeEncoder
from forgesync_edge.replay.application.runtime import (
    ReplayConflictError,
    ReplayRuntime,
    ReplaySessionView,
)
from forgesync_edge.replay.domain import ReplayObservation, ReplaySpeed


class FixedClock:
    def __init__(self, now: datetime) -> None:
        self.value = now

    def now(self) -> datetime:
        return self.value


class StaticReader:
    def __init__(self, observations: tuple[ReplayObservation, ...]) -> None:
        self.observations = observations

    def read(self, canonical_run: Path) -> tuple[ReplayObservation, ...]:
        assert canonical_run == Path("configured")
        return self.observations


class CollectingPublisher:
    def __init__(self) -> None:
        self.envelopes: list[bytes] = []

    def publish(self, envelope: bytes) -> None:
        self.envelopes.append(envelope)


class SlowCollectingPublisher(CollectingPublisher):
    def publish(self, envelope: bytes) -> None:
        time.sleep(0.02)
        super().publish(envelope)


def test_seek_rebuilds_through_target_and_stays_paused() -> None:
    started_at = datetime(2026, 9, 3, tzinfo=UTC)
    observations = _observations(started_at)
    publisher = CollectingPublisher()
    runtime = ReplayRuntime(
        {"source": Path("configured")},
        publisher,
        StaticReader(observations),
        JsonReplayEnvelopeEncoder(),
        FixedClock(started_at),
    )

    prepared = runtime.prepare("Mazak01", "source", ReplaySpeed.X10)
    seeking = runtime.start(prepared.replay_session_id, 0, started_at + timedelta(seconds=1))

    assert seeking.status == "SEEKING"
    paused = _wait_for_status(runtime, "Mazak01", "PAUSED")
    assert paused.publication_cursor is not None
    assert paused.publication_cursor.replay_sequence == 1
    assert len(publisher.envelopes) == 2
    assert json.loads(publisher.envelopes[-1])["replay"]["replaySequence"] == 1


def test_replacing_a_paused_session_stops_its_worker_before_seek() -> None:
    started_at = datetime(2026, 9, 3, tzinfo=UTC)
    observations = _observations(started_at, count=60)
    publisher = SlowCollectingPublisher()
    runtime = ReplayRuntime(
        {"source": Path("configured")},
        publisher,
        StaticReader(observations),
        JsonReplayEnvelopeEncoder(),
        FixedClock(started_at),
    )
    first = runtime.prepare("Mazak01", "source", ReplaySpeed.X10)
    running = runtime.start(first.replay_session_id, first.revision, None)
    _wait_for_publication_count(publisher, 1)
    paused = runtime.pause(running.replay_session_id, running.revision)

    replacement = runtime.replace(
        paused.replay_session_id,
        paused.revision,
        ReplaySpeed.X10,
        observations[-1].source_observed_at,
    )
    runtime.start(
        replacement.replay_session_id, replacement.revision, observations[-1].source_observed_at
    )
    completed = _wait_for_status(runtime, "Mazak01", "COMPLETED", timeout_seconds=4)
    replacement_sequences = [
        json.loads(envelope)["replay"]["replaySequence"]
        for envelope in publisher.envelopes
        if json.loads(envelope)["replay"]["replaySessionId"] == str(replacement.replay_session_id)
    ]

    assert replacement_sequences == list(range(60))
    assert completed.publication_cursor is not None
    assert completed.publication_cursor.replay_sequence == 59


def test_stale_revision_and_second_active_session_are_rejected() -> None:
    now = datetime(2026, 9, 3, tzinfo=UTC)
    runtime = ReplayRuntime(
        {"source": Path("configured")},
        CollectingPublisher(),
        StaticReader(_observations(now)),
        JsonReplayEnvelopeEncoder(),
        FixedClock(now),
    )
    prepared = runtime.prepare("Mazak01", "source", ReplaySpeed.X1)
    running = runtime.start(prepared.replay_session_id, prepared.revision, None)

    with pytest.raises(ReplayConflictError, match="active"):
        runtime.prepare("Mazak01", "source", ReplaySpeed.X1)
    with pytest.raises(ReplayConflictError, match="expectedRevision"):
        runtime.pause(running.replay_session_id, running.revision + 1)


def test_abandoned_preparation_can_be_replaced_by_a_retry() -> None:
    now = datetime(2026, 9, 3, tzinfo=UTC)
    runtime = ReplayRuntime(
        {"source": Path("configured")},
        CollectingPublisher(),
        StaticReader(_observations(now)),
        JsonReplayEnvelopeEncoder(),
        FixedClock(now),
    )

    abandoned = runtime.prepare("Mazak01", "source", ReplaySpeed.X1)
    retried = runtime.prepare("Mazak01", "source", ReplaySpeed.X10)

    assert retried.replay_session_id != abandoned.replay_session_id
    assert retried.speed_multiplier == 10


def test_invalid_seek_target_does_not_replace_the_current_session() -> None:
    now = datetime(2026, 9, 3, tzinfo=UTC)
    runtime = ReplayRuntime(
        {"source": Path("configured")},
        CollectingPublisher(),
        StaticReader(_observations(now)),
        JsonReplayEnvelopeEncoder(),
        FixedClock(now),
    )
    prepared = runtime.prepare("Mazak01", "source", ReplaySpeed.X1)

    with pytest.raises(ValueError, match="inside the source range"):
        runtime.replace(
            prepared.replay_session_id,
            prepared.revision,
            ReplaySpeed.X10,
            now - timedelta(seconds=1),
        )

    current = runtime.current("Mazak01")
    assert current.replay_session_id == prepared.replay_session_id
    assert current.speed_multiplier == 1


def _wait_for_status(
    runtime: ReplayRuntime, machine_id: str, status: str, timeout_seconds: float = 1
) -> ReplaySessionView:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        current = runtime.current(machine_id)
        if current.status == status:
            return current
        time.sleep(0.001)
    raise AssertionError(f"Replay did not reach {status}")


def _wait_for_publication_count(publisher: CollectingPublisher, count: int) -> None:
    deadline = time.monotonic() + 1
    while time.monotonic() < deadline:
        if len(publisher.envelopes) >= count:
            return
        time.sleep(0.001)
    raise AssertionError(f"Replay did not publish {count} observations")


def _observations(started_at: datetime, count: int = 3) -> tuple[ReplayObservation, ...]:
    return tuple(
        ReplayObservation(
            f"event-{index}",
            started_at + timedelta(seconds=index),
            json.dumps(
                {
                    "schemaVersion": "2.0.0",
                    "eventId": str(UUID(int=index + 1)),
                    "sourceEventKey": f"event-{index}",
                    "machineId": "Mazak01",
                    "source": {
                        "sourceObservedAt": (started_at + timedelta(seconds=index)).isoformat()
                    },
                }
            ).encode(),
        )
        for index in range(count)
    )
