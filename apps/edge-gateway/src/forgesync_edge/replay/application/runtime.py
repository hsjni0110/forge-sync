"""Thread-safe runtime orchestration for one active Replay Session per machine."""

from __future__ import annotations

import json
import threading
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID, uuid4

from ..domain import ReplaySession, ReplaySpeed, ReplayStatus
from .ports import Clock, ReplayEnvelopeEncoder, ReplayPublisher, ReplaySourceReader


class ReplayConflictError(ValueError):
    pass


class ReplayNotFoundError(LookupError):
    pass


@dataclass(frozen=True, slots=True)
class ReplayPublicationCursor:
    replay_sequence: int
    source_observed_at: datetime
    replay_published_at: datetime


@dataclass(frozen=True, slots=True)
class ReplaySessionView:
    replay_session_id: UUID
    machine_id: str
    source_set_id: str
    status: str
    speed_multiplier: int
    revision: int
    source_starts_at: datetime
    source_ends_at: datetime
    publication_cursor: ReplayPublicationCursor | None
    failure: str | None = None


class SystemClock:
    def now(self) -> datetime:
        return datetime.now(UTC)


class ReplayRuntime:
    def __init__(
        self,
        sources: dict[str, Path],
        publisher: ReplayPublisher,
        source_reader: ReplaySourceReader,
        encoder: ReplayEnvelopeEncoder,
        clock: Clock | None = None,
    ) -> None:
        self._sources = dict(sources)
        self._publisher = publisher
        self._clock = clock or SystemClock()
        self._source_reader = source_reader
        self._encoder = encoder
        self._condition = threading.Condition()
        self._session: ReplaySession | None = None
        self._machine_id: str | None = None
        self._source_set_id: str | None = None
        self._runtime_status = "PREPARING"
        self._revision = 0
        self._cursor: ReplayPublicationCursor | None = None
        self._failure: str | None = None
        self._worker: threading.Thread | None = None
        self._seek_target: datetime | None = None
        self._is_publishing = False
        self._is_preparing = False

    def prepare(self, machine_id: str, source_set_id: str, speed: ReplaySpeed) -> ReplaySessionView:
        with self._condition:
            if self._is_preparing or (
                self._session is not None
                and self._runtime_status not in {"PREPARING", "COMPLETED", "FAILED"}
            ):
                raise ReplayConflictError("machine already has an active replay session")
            self._is_preparing = True
        try:
            return self._prepare_replacement(machine_id, source_set_id, speed)
        finally:
            with self._condition:
                self._is_preparing = False

    def replace(
        self,
        session_id: UUID,
        expected_revision: int,
        speed: ReplaySpeed,
        seek_target: datetime,
    ) -> ReplaySessionView:
        with self._condition:
            if self._is_preparing:
                raise ReplayConflictError("a replacement replay is already being prepared")
            self._wait_for_publication()
            self._require(session_id, expected_revision)
            assert self._session is not None
            if seek_target.tzinfo is None or seek_target.utcoffset() is None:
                raise ValueError("seek target must include a timezone")
            if not self._session.source_starts_at <= seek_target <= self._session.source_ends_at:
                raise ValueError("seek target must be inside the source range")
            assert self._machine_id is not None
            assert self._source_set_id is not None
            machine_id = self._machine_id
            source_set_id = self._source_set_id
            self._is_preparing = True
        try:
            return self._prepare_replacement(machine_id, source_set_id, speed)
        finally:
            with self._condition:
                self._is_preparing = False

    def _prepare_replacement(
        self, machine_id: str, source_set_id: str, speed: ReplaySpeed
    ) -> ReplaySessionView:
        source_path = self._sources.get(source_set_id)
        if source_path is None:
            raise ValueError("sourceSetId is not configured")
        observations = self._source_reader.read(source_path)
        actual_machine_ids = {_machine_id(item.canonical_envelope) for item in observations}
        if actual_machine_ids != {machine_id}:
            raise ValueError("configured source does not belong to machineId")
        with self._condition:
            self._wait_for_publication()
            self._session = ReplaySession(uuid4(), observations, speed, self._clock.now())
            self._session.pause(self._clock.now())
            self._machine_id = machine_id
            self._source_set_id = source_set_id
            self._runtime_status = "PREPARING"
            self._revision = 0
            self._cursor = None
            self._failure = None
            self._seek_target = None
            return self._view()

    def start(
        self, session_id: UUID, expected_revision: int, seek_target: datetime | None
    ) -> ReplaySessionView:
        with self._condition:
            session = self._require(session_id, expected_revision)
            if self._runtime_status != "PREPARING":
                raise ReplayConflictError("only a prepared replay can be started")
            if seek_target is not None:
                if seek_target.tzinfo is None or seek_target.utcoffset() is None:
                    raise ValueError("seek target must include a timezone")
                if not session.source_starts_at <= seek_target <= session.source_ends_at:
                    raise ValueError("seek target must be inside the source range")
            self._seek_target = seek_target
            self._runtime_status = "SEEKING" if seek_target is not None else "RUNNING"
            session.resume(self._clock.now())
            self._revision += 1
            self._worker = threading.Thread(target=self._publish_loop, daemon=True)
            self._worker.start()
            return self._view()

    def pause(self, session_id: UUID, expected_revision: int) -> ReplaySessionView:
        with self._condition:
            self._wait_for_publication()
            session = self._require(session_id, expected_revision)
            if self._runtime_status != "RUNNING":
                raise ReplayConflictError("only a running replay can be paused")
            session.pause(self._clock.now())
            self._runtime_status = "PAUSED"
            self._revision += 1
            self._condition.notify_all()
            return self._view()

    def resume(self, session_id: UUID, expected_revision: int) -> ReplaySessionView:
        with self._condition:
            self._wait_for_publication()
            session = self._require(session_id, expected_revision)
            if self._runtime_status != "PAUSED":
                raise ReplayConflictError("only a paused replay can be resumed")
            session.resume(self._clock.now())
            self._runtime_status = "RUNNING"
            self._revision += 1
            self._condition.notify_all()
            return self._view()

    def change_speed(
        self, session_id: UUID, expected_revision: int, speed: ReplaySpeed
    ) -> ReplaySessionView:
        with self._condition:
            self._wait_for_publication()
            session = self._require(session_id, expected_revision)
            if self._runtime_status not in {"RUNNING", "PAUSED"}:
                raise ReplayConflictError("replay speed cannot be changed in the current state")
            session.change_speed(speed, self._clock.now())
            self._revision += 1
            self._condition.notify_all()
            return self._view()

    def current(self, machine_id: str) -> ReplaySessionView:
        with self._condition:
            if self._session is None or self._machine_id != machine_id:
                raise ReplayNotFoundError("active replay session was not found")
            return self._view()

    def _publish_loop(self) -> None:
        while True:
            with self._condition:
                session = self._session
                if session is None or self._runtime_status in {"FAILED", "COMPLETED"}:
                    return
                if self._runtime_status == "PAUSED":
                    self._condition.wait()
                    continue
                now = self._clock.now()
                if self._runtime_status == "RUNNING" and not session.is_due(now):
                    delay = max((session.next_due_at - now).total_seconds(), 0.001)
                    self._condition.wait(timeout=min(delay, 1.0))
                    continue
                observation = session.current_observation
                sequence = session.replay_sequence
                self._is_publishing = True
            published_at = self._clock.now()
            try:
                envelope = self._encoder.add_replay_identity(
                    observation, session.replay_session_id, sequence, published_at
                )
                self._publisher.publish(envelope)
            except Exception as error:  # Adapter failures become an explicit stable runtime state.
                with self._condition:
                    self._is_publishing = False
                    self._runtime_status = "FAILED"
                    self._failure = type(error).__name__
                    self._condition.notify_all()
                return
            with self._condition:
                self._is_publishing = False
                self._condition.notify_all()
                if self._session is not session:
                    return
                if self._runtime_status == "SEEKING":
                    session.record_seek_publication(published_at)
                else:
                    session.record_successful_publication(published_at)
                self._cursor = ReplayPublicationCursor(
                    sequence.value, observation.source_observed_at, published_at
                )
                if session.status is ReplayStatus.COMPLETED:
                    self._runtime_status = "COMPLETED"
                    self._condition.notify_all()
                    return
                if self._seek_target is not None:
                    following = session.current_observation.source_observed_at
                    if following > self._seek_target:
                        session.pause(self._clock.now())
                        self._runtime_status = "PAUSED"
                        self._seek_target = None
                        self._revision += 1
                        self._condition.notify_all()

    def _require(self, session_id: UUID, expected_revision: int) -> ReplaySession:
        if self._is_preparing:
            raise ReplayConflictError("Replay preparation is in progress")
        if self._session is None or self._session.replay_session_id != session_id:
            raise ReplayNotFoundError("replay session was not found")
        if self._revision != expected_revision:
            raise ReplayConflictError("expectedRevision does not match the authoritative state")
        return self._session

    def _wait_for_publication(self) -> None:
        while self._is_publishing:
            self._condition.wait()

    def _view(self) -> ReplaySessionView:
        assert self._session is not None
        assert self._machine_id is not None
        assert self._source_set_id is not None
        return ReplaySessionView(
            self._session.replay_session_id,
            self._machine_id,
            self._source_set_id,
            self._runtime_status,
            self._session.speed.value,
            self._revision,
            self._session.source_starts_at,
            self._session.source_ends_at,
            self._cursor,
            self._failure,
        )


def _machine_id(envelope: bytes) -> str:
    value = json.loads(envelope).get("machineId")
    if not isinstance(value, str):
        raise ValueError("Canonical observation lacks machineId")
    return value
