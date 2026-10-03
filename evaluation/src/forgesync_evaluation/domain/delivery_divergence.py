"""How far a faulted run drifts from the fault-free run of the same consumer."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from itertools import pairwise

from .naive_stream import ConsumerOutcome, TimelineSegment


@dataclass(frozen=True, slots=True)
class DeliveryDivergence:
    final_value_mismatch_count: int
    rollback_count: int | None
    misattributed_seconds: float
    active_entry_difference: int
    extra_alarm_opening_count: int


def misattributed_seconds(
    baseline: Sequence[TimelineSegment], observed: Sequence[TimelineSegment]
) -> float:
    boundaries = sorted(
        {moment for item in (*baseline, *observed) for moment in (item.started_at, item.ended_at)}
    )
    differing_seconds = 0.0
    for opened, closed in pairwise(boundaries):
        midpoint = opened + (closed - opened) / 2
        if _value_at(baseline, midpoint) != _value_at(observed, midpoint):
            differing_seconds += (closed - opened).total_seconds()
    return differing_seconds


def diverge(
    baseline: ConsumerOutcome, observed: ConsumerOutcome, tracked_item_ids: Sequence[str]
) -> DeliveryDivergence:
    return DeliveryDivergence(
        final_value_mismatch_count=sum(
            baseline.final_values.get(item_id) != observed.final_values.get(item_id)
            for item_id in tracked_item_ids
        ),
        rollback_count=observed.rollback_count,
        misattributed_seconds=misattributed_seconds(
            baseline.execution_timeline, observed.execution_timeline
        ),
        active_entry_difference=observed.active_entry_count - baseline.active_entry_count,
        extra_alarm_opening_count=observed.alarm_opening_count - baseline.alarm_opening_count,
    )


_UNCOVERED = object()


def _value_at(timeline: Sequence[TimelineSegment], moment: datetime) -> object:
    """Where segments overlap, the one that arrived last is what the dashboard shows."""
    covering = [item.value for item in timeline if item.started_at <= moment < item.ended_at]
    return covering[-1] if covering else _UNCOVERED
