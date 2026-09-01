"""Consumer-shaped ports for replay infrastructure."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import Protocol
from uuid import UUID

from ..domain import ReplayObservation, ReplaySequence


class Clock(Protocol):
    def now(self) -> datetime: ...


class ReplaySessionIdGenerator(Protocol):
    def new(self) -> UUID: ...


class ReplaySourceReader(Protocol):
    def read(self, canonical_run: Path) -> tuple[ReplayObservation, ...]: ...


class ReplayEnvelopeEncoder(Protocol):
    def add_replay_identity(
        self,
        observation: ReplayObservation,
        replay_session_id: UUID,
        replay_sequence: ReplaySequence,
        replay_published_at: datetime,
    ) -> bytes: ...


class ReplayPublisher(Protocol):
    def publish(self, envelope: bytes) -> None: ...
