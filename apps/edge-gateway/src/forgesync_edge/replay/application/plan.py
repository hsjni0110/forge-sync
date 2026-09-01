"""Deterministic replay schedule summary without sleeping or publishing."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from ..domain import ReplayObservation, ReplaySpeed, order_observations, scaled_interval


@dataclass(frozen=True, slots=True)
class ReplayPlan:
    replay_session_id: UUID
    speed: ReplaySpeed
    observation_count: int
    source_starts_at: datetime
    source_ends_at: datetime
    replay_starts_at: datetime
    replay_ends_at: datetime
    sequence_hash: str


def build_replay_plan(
    observations: tuple[ReplayObservation, ...],
    replay_session_id: UUID,
    replay_starts_at: datetime,
    speed: ReplaySpeed,
) -> ReplayPlan:
    if replay_starts_at.tzinfo is None or replay_starts_at.utcoffset() is None:
        raise ValueError("replay start must include a timezone")
    ordered = order_observations(observations)
    if not ordered:
        raise ValueError("replay requires at least one observation")
    sequence_digest = hashlib.sha256()
    replay_ends_at = replay_starts_at
    previous: ReplayObservation | None = None
    for sequence, observation in enumerate(ordered):
        identity = {"replaySequence": sequence, "sourceEventKey": observation.source_event_key}
        sequence_digest.update(
            json.dumps(identity, separators=(",", ":"), sort_keys=True).encode() + b"\n"
        )
        if previous is not None:
            replay_ends_at += scaled_interval(
                previous.source_observed_at, observation.source_observed_at, speed
            )
        previous = observation
    return ReplayPlan(
        replay_session_id=replay_session_id,
        speed=speed,
        observation_count=len(ordered),
        source_starts_at=ordered[0].source_observed_at,
        source_ends_at=ordered[-1].source_observed_at,
        replay_starts_at=replay_starts_at,
        replay_ends_at=replay_ends_at,
        sequence_hash=f"sha256:{sequence_digest.hexdigest()}",
    )
