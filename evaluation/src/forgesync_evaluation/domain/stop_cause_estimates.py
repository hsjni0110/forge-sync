"""Preregistered naive stop-cause rule (NA-CAUSE-1)."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

from .naive_estimate import NaiveEstimate, SourceReading


@dataclass(frozen=True, slots=True)
class DowntimeInterval:
    """A closed downtime interval taken as-is from the ForgeSync Downtime Pareto."""

    started_at: datetime
    ended_at: datetime


NON_NORMAL_CONDITION_LEVELS = frozenset({"Warning", "Fault"})


def estimate_stop_causes(
    downtimes: Sequence[DowntimeInterval], readings: Sequence[SourceReading]
) -> NaiveEstimate:
    non_normal_conditions = [reading for reading in readings if _is_non_normal_condition(reading)]
    stops_given_a_cause = sum(
        1
        for downtime in downtimes
        if any(_observed_during(condition, downtime) for condition in non_normal_conditions)
    )
    return NaiveEstimate(
        rule_id="NA-CAUSE-1",
        value=stops_given_a_cause,
        hidden_assumptions=("CONCURRENT_CONDITION_IS_CAUSE",),
    )


def _is_non_normal_condition(reading: SourceReading) -> bool:
    """Without a catalog, a naive reader recognises a condition by its leading level field."""
    level, separator, _ = reading.value.partition("|")
    return bool(separator) and level in NON_NORMAL_CONDITION_LEVELS


def _observed_during(condition: SourceReading, downtime: DowntimeInterval) -> bool:
    return downtime.started_at <= condition.source_observed_at < downtime.ended_at
