"""Replay application use cases."""

from __future__ import annotations

from pathlib import Path

from ..domain import ReplaySession, ReplaySpeed
from .ports import (
    Clock,
    ReplayEnvelopeEncoder,
    ReplayPublisher,
    ReplaySessionIdGenerator,
    ReplaySourceReader,
)


class StartReplay:
    def __init__(
        self,
        source_reader: ReplaySourceReader,
        session_id_generator: ReplaySessionIdGenerator,
        clock: Clock,
    ) -> None:
        self._source_reader = source_reader
        self._session_id_generator = session_id_generator
        self._clock = clock

    def start(self, canonical_run: Path, speed: ReplaySpeed) -> ReplaySession:
        observations = self._source_reader.read(canonical_run)
        return ReplaySession(
            self._session_id_generator.new(), observations, speed, self._clock.now()
        )


class PauseReplay:
    def __init__(self, clock: Clock) -> None:
        self._clock = clock

    def pause(self, session: ReplaySession) -> None:
        session.pause(self._clock.now())


class ResumeReplay:
    def __init__(self, clock: Clock) -> None:
        self._clock = clock

    def resume(self, session: ReplaySession) -> None:
        session.resume(self._clock.now())


class ChangeReplaySpeed:
    def __init__(self, clock: Clock) -> None:
        self._clock = clock

    def change(self, session: ReplaySession, speed: ReplaySpeed) -> None:
        session.change_speed(speed, self._clock.now())


class PublishDueObservation:
    def __init__(
        self,
        clock: Clock,
        encoder: ReplayEnvelopeEncoder,
        publisher: ReplayPublisher,
    ) -> None:
        self._clock = clock
        self._encoder = encoder
        self._publisher = publisher

    def publish_next(self, session: ReplaySession) -> bool:
        published_at = self._clock.now()
        if not session.is_due(published_at):
            return False
        envelope = self._encoder.add_replay_identity(
            session.current_observation,
            session.replay_session_id,
            session.replay_sequence,
            published_at,
        )
        self._publisher.publish(envelope)
        session.record_successful_publication(published_at)
        return True
