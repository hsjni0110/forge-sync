from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import pytest
from forgesync_edge.replay.adapter.inbound.cli import main as replay_cli
from forgesync_edge.replay.adapter.outbound.filesystem_source import FilesystemReplaySourceReader
from forgesync_edge.replay.adapter.outbound.json_envelope import JsonReplayEnvelopeEncoder
from forgesync_edge.replay.application.plan import build_replay_plan
from forgesync_edge.replay.application.ports import ReplaySourceReader
from forgesync_edge.replay.application.service import (
    ChangeReplaySpeed,
    PauseReplay,
    PublishDueObservation,
    ResumeReplay,
    StartReplay,
)
from forgesync_edge.replay.domain.model import (
    ReplayObservation,
    ReplaySpeed,
    ReplayStatus,
)
from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY_ROOT = Path(__file__).parents[3]
SCHEMA_PATH = (
    REPOSITORY_ROOT
    / "contracts"
    / "observation-envelope"
    / "v2"
    / "observation-envelope.schema.json"
)
SESSION_ID = UUID("61c7fe98-d1cd-4a2c-9ea8-24f72cc714db")
STARTED_AT = datetime(2026, 9, 1, 1, 2, 3, tzinfo=UTC)


@dataclass
class FixedClock:
    current: datetime

    def now(self) -> datetime:
        return self.current


@dataclass(frozen=True)
class FixedSessionIdGenerator:
    value: UUID = SESSION_ID

    def new(self) -> UUID:
        return self.value


@dataclass(frozen=True)
class StaticReader(ReplaySourceReader):
    observations: tuple[ReplayObservation, ...]

    def read(self, canonical_run: Path) -> tuple[ReplayObservation, ...]:
        return self.observations


class CollectingPublisher:
    def __init__(self, *, fail: bool = False) -> None:
        self.documents: list[bytes] = []
        self.fail = fail

    def publish(self, envelope: bytes) -> None:
        if self.fail:
            raise RuntimeError("publisher unavailable")
        self.documents.append(envelope)


def test_fixed_clock_preserves_order_and_scales_intervals() -> None:
    observations = _observations(("later", 10), ("first", 0), ("middle", 5))

    one_x = _published_schedule(observations, ReplaySpeed.X1)
    ten_x = _published_schedule(observations, ReplaySpeed.X10)

    assert [key for key, _ in one_x] == ["first", "middle", "later"]
    assert [key for key, _ in ten_x] == ["first", "middle", "later"]
    assert [published_at for _, published_at in one_x] == [
        STARTED_AT,
        STARTED_AT + timedelta(seconds=5),
        STARTED_AT + timedelta(seconds=10),
    ]
    assert [published_at for _, published_at in ten_x] == [
        STARTED_AT,
        STARTED_AT + timedelta(milliseconds=500),
        STARTED_AT + timedelta(seconds=1),
    ]


def test_pause_resume_and_speed_change_preserve_position_without_duplicates() -> None:
    clock = FixedClock(STARTED_AT)
    publisher = CollectingPublisher()
    session = _start(clock, _observations(("first", 0), ("second", 10)))
    publish = PublishDueObservation(clock, JsonReplayEnvelopeEncoder(), publisher)
    assert publish.publish_next(session) is True
    clock.current += timedelta(seconds=2)

    PauseReplay(clock).pause(session)
    paused_due_at = session.next_due_at
    clock.current += timedelta(hours=1)
    assert publish.publish_next(session) is False
    assert session.replay_sequence.value == 1

    ResumeReplay(clock).resume(session)
    assert session.next_due_at == clock.current + timedelta(seconds=8)
    ChangeReplaySpeed(clock).change(session, ReplaySpeed.X10)
    assert session.next_due_at == clock.current + timedelta(milliseconds=800)
    assert paused_due_at == STARTED_AT + timedelta(seconds=10)
    clock.current = session.next_due_at
    assert publish.publish_next(session) is True
    assert session.status is ReplayStatus.COMPLETED
    assert [json.loads(item)["sourceEventKey"] for item in publisher.documents] == [
        "first",
        "second",
    ]


def test_equal_source_time_uses_source_event_key_and_does_not_invent_source_identity() -> None:
    observations = _observations(("z-key", 0), ("a-key", 0))
    clock = FixedClock(STARTED_AT)
    publisher = CollectingPublisher()
    session = _start(clock, observations)
    publish = PublishDueObservation(clock, JsonReplayEnvelopeEncoder(), publisher)

    assert publish.publish_next(session) is True
    assert publish.publish_next(session) is True
    documents = [json.loads(item) for item in publisher.documents]

    assert [item["sourceEventKey"] for item in documents] == ["a-key", "z-key"]
    assert all("agentInstanceId" not in item["source"] for item in documents)
    assert all("sourceSequence" not in item["source"] for item in documents)
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    assert [error for item in documents for error in validator.iter_errors(item)] == []


def test_publish_failure_keeps_same_observation_and_sequence_for_retry() -> None:
    clock = FixedClock(STARTED_AT)
    session = _start(clock, _observations(("only", 0)))
    failing = PublishDueObservation(
        clock, JsonReplayEnvelopeEncoder(), CollectingPublisher(fail=True)
    )

    with pytest.raises(RuntimeError, match="publisher unavailable"):
        failing.publish_next(session)

    assert session.replay_sequence.value == 0
    assert session.current_observation.source_event_key == "only"
    assert session.status is ReplayStatus.RUNNING


def test_invalid_speed_and_completed_resume_are_rejected() -> None:
    with pytest.raises(ValueError, match="speed"):
        ReplaySpeed.from_multiplier(2)
    clock = FixedClock(STARTED_AT)
    session = _start(clock, _observations(("only", 0)))
    PublishDueObservation(clock, JsonReplayEnvelopeEncoder(), CollectingPublisher()).publish_next(
        session
    )

    with pytest.raises(ValueError, match="completed"):
        ResumeReplay(clock).resume(session)


def test_filesystem_reader_verifies_manifest_and_rejects_existing_replay(tmp_path: Path) -> None:
    run_directory = _write_run(tmp_path, _observation_document("source-key", 0))
    reader = FilesystemReplaySourceReader()

    observations = reader.read(run_directory)
    assert observations[0].source_event_key == "source-key"

    manifest_path = run_directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["outputs"]["observations.ndjson"]["sha256"] = "0" * 64
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(ValueError, match="checksum"):
        reader.read(run_directory)

    replayed = _observation_document("source-key", 0)
    replayed["replay"] = {
        "replaySessionId": str(SESSION_ID),
        "replaySequence": 0,
        "replayPublishedAt": STARTED_AT.isoformat(),
    }
    replayed_run = _write_run(tmp_path / "replayed", replayed)
    with pytest.raises(ValueError, match="already contains replay identity"):
        reader.read(replayed_run)


def test_replay_plan_is_deterministic_and_sequence_hash_ignores_speed() -> None:
    observations = _observations(("second", 1), ("first", 0), ("same-time", 1))

    one_x = build_replay_plan(observations, SESSION_ID, STARTED_AT, ReplaySpeed.X1)
    hundred_x = build_replay_plan(observations, SESSION_ID, STARTED_AT, ReplaySpeed.X100)

    assert one_x.sequence_hash == hundred_x.sequence_hash
    assert one_x.observation_count == 3
    assert one_x.replay_ends_at == STARTED_AT + timedelta(seconds=1)
    assert hundred_x.replay_ends_at == STARTED_AT + timedelta(milliseconds=10)
    assert one_x == build_replay_plan(observations, SESSION_ID, STARTED_AT, ReplaySpeed.X1)


def test_plan_command_writes_reusable_evidence_without_publishing(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    run_directory = _write_run(tmp_path / "canonical", _observation_document("source-key", 0))
    output = tmp_path / "report"
    arguments = [
        "plan",
        "--canonical-run",
        str(run_directory),
        "--session-id",
        str(SESSION_ID),
        "--started-at",
        "2026-09-01T01:02:03Z",
        "--speed",
        "100",
        "--output",
        str(output),
    ]

    assert replay_cli(arguments) == 0
    first_json = (output / "replay-plan.json").read_bytes()
    assert replay_cli(arguments) == 0

    assert (output / "replay-plan.json").read_bytes() == first_json
    assert "does not claim that messages were published" in (output / "replay-plan.md").read_text(
        encoding="utf-8"
    )
    assert capsys.readouterr().err == ""


def _start(clock: FixedClock, observations: tuple[ReplayObservation, ...]):
    return StartReplay(StaticReader(observations), FixedSessionIdGenerator(), clock).start(
        Path("unused"), ReplaySpeed.X1
    )


def _published_schedule(
    observations: tuple[ReplayObservation, ...], speed: ReplaySpeed
) -> list[tuple[str, datetime]]:
    clock = FixedClock(STARTED_AT)
    publisher = CollectingPublisher()
    session = StartReplay(StaticReader(observations), FixedSessionIdGenerator(), clock).start(
        Path("unused"), speed
    )
    use_case = PublishDueObservation(clock, JsonReplayEnvelopeEncoder(), publisher)
    schedule: list[tuple[str, datetime]] = []
    while session.status is ReplayStatus.RUNNING:
        clock.current = session.next_due_at
        key = session.current_observation.source_event_key
        assert use_case.publish_next(session) is True
        schedule.append((key, clock.current))
    return schedule


def _observations(*items: tuple[str, int]) -> tuple[ReplayObservation, ...]:
    return tuple(
        ReplayObservation(
            source_event_key=key,
            source_observed_at=datetime(2016, 10, 5, tzinfo=UTC) + timedelta(seconds=seconds),
            canonical_envelope=json.dumps(
                _observation_document(key, seconds), separators=(",", ":"), sort_keys=True
            ).encode(),
        )
        for key, seconds in items
    )


def _observation_document(key: str, seconds: int) -> dict[str, object]:
    document = json.loads(
        (REPOSITORY_ROOT / "tests/fixtures/canonical/v2/valid/event-execution.json").read_text(
            encoding="utf-8"
        )
    )
    document["sourceEventKey"] = key
    document.pop("replay", None)
    document["source"]["sourceObservedAt"] = (  # type: ignore[index]
        datetime(2016, 10, 5, tzinfo=UTC) + timedelta(seconds=seconds)
    ).isoformat()
    document["source"].pop("agentInstanceId", None)  # type: ignore[union-attr]
    document["source"].pop("sourceSequence", None)  # type: ignore[union-attr]
    return document


def _write_run(root: Path, document: dict[str, object]) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    content = json.dumps(document, separators=(",", ":"), sort_keys=True).encode() + b"\n"
    (root / "observations.ndjson").write_bytes(content)
    manifest = {
        "processingRunId": "sha256:" + "a" * 64,
        "observationCount": 1,
        "outputs": {
            "observations.ndjson": {
                "byteLength": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
            }
        },
    }
    (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    return root
