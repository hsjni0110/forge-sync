"""A preregistered naive consumer: last write wins in arrival order, with no Inbox."""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime
from itertools import pairwise


@dataclass(frozen=True, slots=True)
class DeliveredObservation:
    """One delivered envelope; `value` is None when the source reported UNAVAILABLE."""

    source_data_item_id: str
    source_observed_at: datetime
    value: str | None
    is_non_normal_condition: bool


@dataclass(frozen=True, slots=True)
class TimelineSegment:
    started_at: datetime
    ended_at: datetime
    value: str | None


@dataclass(frozen=True, slots=True)
class NaiveStreamOutcome:
    final_values: Mapping[str, str | None]
    rollback_count: int
    execution_timeline: tuple[TimelineSegment, ...]
    active_entry_count: int
    alarm_opening_count: int


ACTIVE = "ACTIVE"


def consume_naively(
    observations: Sequence[DeliveredObservation], execution_item_id: str
) -> NaiveStreamOutcome:
    held_at: dict[str, datetime] = {}
    final_values: dict[str, str | None] = {}
    rollbacks = 0
    executions = [item for item in observations if item.source_data_item_id == execution_item_id]
    for observation in observations:
        item_id = observation.source_data_item_id
        if item_id in held_at and observation.source_observed_at < held_at[item_id]:
            rollbacks += 1
        held_at[item_id] = observation.source_observed_at
        final_values[item_id] = observation.value
    return NaiveStreamOutcome(
        final_values=final_values,
        rollback_count=rollbacks,
        execution_timeline=_timeline(executions, observations),
        active_entry_count=sum(
            1
            for previous, current in pairwise(executions)
            if previous.value != ACTIVE and current.value == ACTIVE
        ),
        alarm_opening_count=sum(item.is_non_normal_condition for item in observations),
    )


def _timeline(
    executions: Sequence[DeliveredObservation], observations: Sequence[DeliveredObservation]
) -> tuple[TimelineSegment, ...]:
    """Each value lasts until the next arrival's source time; a negative span is cut to zero."""
    if not executions:
        return ()
    boundaries = [item.source_observed_at for item in executions[1:]]
    boundaries.append(observations[-1].source_observed_at)
    return tuple(
        TimelineSegment(
            opened.source_observed_at, max(closed_at, opened.source_observed_at), opened.value
        )
        for opened, closed_at in zip(executions, boundaries, strict=True)
    )
