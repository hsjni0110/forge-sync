"""Preregistered naive utilization rules (NA-UTIL-*)."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence
from itertools import pairwise

from .naive_estimate import (
    ACTIVE,
    EXECUTION,
    UNAVAILABLE,
    NaiveEstimate,
    SourceReading,
    available_integer_values,
)

AUTOMATIC_TIME_COUNTER = "auto_time"
TOTAL_TIME_COUNTER = "total_time"


def estimate_utilization_over_observed_span(readings: Sequence[SourceReading]) -> NaiveEstimate:
    span_seconds = _observed_span_seconds(readings)
    active_seconds = execution_dwell_seconds(readings).get(ACTIVE, 0.0)
    return NaiveEstimate(
        rule_id="NA-UTIL-1",
        value=active_seconds / span_seconds if span_seconds else None,
        hidden_assumptions=("UNAVAILABLE_MEANS_NOT_OPERATING",),
    )


def estimate_utilization_over_available_time(readings: Sequence[SourceReading]) -> NaiveEstimate:
    dwell = execution_dwell_seconds(readings)
    available_seconds = sum(dwell.values()) - dwell.get(UNAVAILABLE, 0.0)
    return NaiveEstimate(
        rule_id="NA-UTIL-2",
        value=dwell.get(ACTIVE, 0.0) / available_seconds if available_seconds else None,
        hidden_assumptions=("MISSING_TIME_MATCHES_OBSERVED_TIME",),
    )


def estimate_utilization_from_accumulated_counters(
    readings: Sequence[SourceReading],
) -> NaiveEstimate:
    automatic_growth = _counter_growth(readings, AUTOMATIC_TIME_COUNTER)
    total_growth = _counter_growth(readings, TOTAL_TIME_COUNTER)
    return NaiveEstimate(
        rule_id="NA-UTIL-3",
        value=automatic_growth / total_growth if total_growth else None,
        hidden_assumptions=("COUNTERS_SHARE_UNIT_AND_MEANING",),
    )


def _observed_span_seconds(readings: Sequence[SourceReading]) -> float:
    if not readings:
        return 0.0
    return (readings[-1].source_observed_at - readings[0].source_observed_at).total_seconds()


def execution_dwell_seconds(readings: Sequence[SourceReading]) -> dict[str, float]:
    """A naive dashboard holds each execution value until the next one or the last reading."""
    executions = [reading for reading in readings if reading.data_item_name == EXECUTION]
    if not executions:
        return {}
    boundaries = [*executions, readings[-1]]
    dwell: defaultdict[str, float] = defaultdict(float)
    for opened, closed in pairwise(boundaries):
        seconds = (closed.source_observed_at - opened.source_observed_at).total_seconds()
        dwell[opened.value] += seconds
    return dwell


def _counter_growth(readings: Sequence[SourceReading], counter_name: str) -> int:
    """A naive dashboard subtracts the first available value from the last one."""
    values = available_integer_values(readings, counter_name)
    return values[-1] - values[0] if values else 0
