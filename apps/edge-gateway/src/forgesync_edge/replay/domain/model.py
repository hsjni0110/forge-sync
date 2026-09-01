"""Replay lifecycle and timing rules independent of JSON, files, and transports."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum, IntEnum
from uuid import UUID


class ReplaySpeed(IntEnum):
    X1 = 1
    X10 = 10
    X100 = 100

    @classmethod
    def from_multiplier(cls, multiplier: int) -> ReplaySpeed:
        try:
            return cls(multiplier)
        except ValueError as error:
            raise ValueError("replay speed must be 1, 10, or 100") from error


class ReplayStatus(Enum):
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    COMPLETED = "COMPLETED"


@dataclass(frozen=True, slots=True, order=True)
class ReplaySequence:
    value: int

    def __post_init__(self) -> None:
        if self.value < 0:
            raise ValueError("replay sequence must be non-negative")

    def next(self) -> ReplaySequence:
        return ReplaySequence(self.value + 1)


@dataclass(frozen=True, slots=True)
class ReplayObservation:
    source_event_key: str
    source_observed_at: datetime
    canonical_envelope: bytes

    def __post_init__(self) -> None:
        if not self.source_event_key:
            raise ValueError("source event key must not be empty")
        _require_aware(self.source_observed_at, "source observed at")
        if not self.canonical_envelope:
            raise ValueError("canonical envelope must not be empty")


def order_observations(
    observations: tuple[ReplayObservation, ...],
) -> tuple[ReplayObservation, ...]:
    return tuple(
        sorted(observations, key=lambda item: (item.source_observed_at, item.source_event_key))
    )


def scaled_interval(earlier: datetime, later: datetime, speed: ReplaySpeed) -> timedelta:
    if later < earlier:
        raise ValueError("ordered source time must not move backwards")
    source_microseconds = (later - earlier) // timedelta(microseconds=1)
    replay_microseconds = (source_microseconds + speed.value - 1) // speed.value
    return timedelta(microseconds=replay_microseconds)


class ReplayClock:
    """Controls only replay publication timing; source time remains immutable."""

    def __init__(self, speed: ReplaySpeed, first_due_at: datetime) -> None:
        _require_aware(first_due_at, "first due at")
        self._speed = speed
        self._next_due_at = first_due_at
        self._remaining_delay: timedelta | None = None

    @property
    def speed(self) -> ReplaySpeed:
        return self._speed

    @property
    def next_due_at(self) -> datetime:
        return self._next_due_at

    @property
    def is_paused(self) -> bool:
        return self._remaining_delay is not None

    def is_due(self, now: datetime) -> bool:
        _require_aware(now, "current time")
        return not self.is_paused and now >= self._next_due_at

    def schedule_following(
        self, published_at: datetime, current_source_at: datetime, following_source_at: datetime
    ) -> None:
        if self.is_paused:
            raise ValueError("paused replay clock cannot schedule publication")
        self._next_due_at = published_at + scaled_interval(
            current_source_at, following_source_at, self._speed
        )

    def pause(self, now: datetime) -> None:
        if self.is_paused:
            raise ValueError("replay clock is already paused")
        _require_aware(now, "current time")
        self._remaining_delay = max(self._next_due_at - now, timedelta())

    def resume(self, now: datetime) -> None:
        if self._remaining_delay is None:
            raise ValueError("replay clock is not paused")
        _require_aware(now, "current time")
        self._next_due_at = now + self._remaining_delay
        self._remaining_delay = None

    def change_speed(self, speed: ReplaySpeed, now: datetime) -> None:
        _require_aware(now, "current time")
        remaining = (
            self._remaining_delay
            if self._remaining_delay is not None
            else max(self._next_due_at - now, timedelta())
        )
        scaled = _rescale_remaining(remaining, self._speed, speed)
        self._speed = speed
        if self._remaining_delay is not None:
            self._remaining_delay = scaled
        else:
            self._next_due_at = now + scaled


class ReplaySession:
    """A serial, in-memory replay aggregate with explicit lifecycle actions."""

    def __init__(
        self,
        replay_session_id: UUID,
        observations: tuple[ReplayObservation, ...],
        speed: ReplaySpeed,
        started_at: datetime,
    ) -> None:
        _require_aware(started_at, "started at")
        if not observations:
            raise ValueError("replay requires at least one observation")
        self._replay_session_id = replay_session_id
        self._observations = order_observations(observations)
        self._clock = ReplayClock(speed, started_at)
        self._status = ReplayStatus.RUNNING
        self._replay_sequence = ReplaySequence(0)

    @property
    def replay_session_id(self) -> UUID:
        return self._replay_session_id

    @property
    def speed(self) -> ReplaySpeed:
        return self._clock.speed

    @property
    def status(self) -> ReplayStatus:
        return self._status

    @property
    def replay_sequence(self) -> ReplaySequence:
        return self._replay_sequence

    @property
    def next_due_at(self) -> datetime:
        return self._clock.next_due_at

    @property
    def current_observation(self) -> ReplayObservation:
        if self._status is ReplayStatus.COMPLETED:
            raise ValueError("completed replay has no current observation")
        return self._observations[self._replay_sequence.value]

    def is_due(self, now: datetime) -> bool:
        _require_aware(now, "current time")
        return self._status is ReplayStatus.RUNNING and self._clock.is_due(now)

    def record_successful_publication(self, published_at: datetime) -> None:
        if self._status is not ReplayStatus.RUNNING:
            raise ValueError("only a running replay can publish")
        if not self.is_due(published_at):
            raise ValueError("observation is not due")
        current_index = self._replay_sequence.value
        if current_index + 1 == len(self._observations):
            self._status = ReplayStatus.COMPLETED
            return
        current = self._observations[current_index]
        following = self._observations[current_index + 1]
        self._replay_sequence = self._replay_sequence.next()
        self._clock.schedule_following(
            published_at, current.source_observed_at, following.source_observed_at
        )

    def pause(self, now: datetime) -> None:
        if self._status is not ReplayStatus.RUNNING:
            raise ValueError("only a running replay can be paused")
        self._clock.pause(now)
        self._status = ReplayStatus.PAUSED

    def resume(self, now: datetime) -> None:
        if self._status is ReplayStatus.COMPLETED:
            raise ValueError("completed replay cannot be resumed")
        if self._status is not ReplayStatus.PAUSED:
            raise ValueError("only a paused replay can be resumed")
        self._clock.resume(now)
        self._status = ReplayStatus.RUNNING

    def change_speed(self, speed: ReplaySpeed, now: datetime) -> None:
        if self._status is ReplayStatus.COMPLETED:
            raise ValueError("completed replay speed cannot be changed")
        self._clock.change_speed(speed, now)


def _rescale_remaining(
    remaining: timedelta, previous_speed: ReplaySpeed, new_speed: ReplaySpeed
) -> timedelta:
    microseconds = remaining // timedelta(microseconds=1)
    numerator = microseconds * previous_speed.value
    scaled_microseconds = (numerator + new_speed.value - 1) // new_speed.value
    return timedelta(microseconds=scaled_microseconds)


def _require_aware(value: datetime, field_name: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must include a timezone")
