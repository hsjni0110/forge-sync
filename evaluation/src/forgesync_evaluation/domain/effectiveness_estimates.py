"""Preregistered naive OEE rules (NA-OEE-*)."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass

from .naive_estimate import ACTIVE, EXECUTION, NaiveEstimate, SourceReading
from .utilization_estimates import (
    estimate_utilization_over_available_time,
    estimate_utilization_over_observed_span,
    execution_dwell_seconds,
)

PROGRAM = "program"
PERFORMANCE_ASSUMPTIONS = ("ONE_ACTIVE_ENTRY_IS_ONE_PART", "SHORTEST_ACTIVE_STRETCH_IS_IDEAL_CYCLE")
QUALITY_ASSUMPTION = "NO_QUALITY_DATA_MEANS_ALL_GOOD"


@dataclass(frozen=True, slots=True)
class _ActiveStretch:
    program: str | None
    seconds: float


def estimate_effectiveness_over_observed_span(readings: Sequence[SourceReading]) -> NaiveEstimate:
    return _effectiveness(
        "NA-OEE-1",
        estimate_utilization_over_observed_span(readings),
        "OBSERVED_SPAN_IS_PLANNED_PRODUCTION_TIME",
        readings,
    )


def estimate_effectiveness_over_available_time(readings: Sequence[SourceReading]) -> NaiveEstimate:
    return _effectiveness(
        "NA-OEE-2",
        estimate_utilization_over_available_time(readings),
        "AVAILABLE_TIME_IS_PLANNED_PRODUCTION_TIME",
        readings,
    )


def _effectiveness(
    rule_id: str,
    utilization: NaiveEstimate,
    planned_time_assumption: str,
    readings: Sequence[SourceReading],
) -> NaiveEstimate:
    """Availability x Performance x Quality, with Quality fixed at 1 as the naive rule does."""
    performance = _performance(readings)
    hidden_assumptions = (
        *utilization.hidden_assumptions,
        planned_time_assumption,
        *PERFORMANCE_ASSUMPTIONS,
        QUALITY_ASSUMPTION,
    )
    if utilization.value is None or performance is None:
        return NaiveEstimate(rule_id, None, hidden_assumptions)
    return NaiveEstimate(rule_id, utilization.value * performance, hidden_assumptions)


def _performance(readings: Sequence[SourceReading]) -> float | None:
    stretches = _active_stretches(readings)
    active_seconds = execution_dwell_seconds(readings).get(ACTIVE, 0.0)
    if not stretches or not active_seconds:
        return None
    stretch_seconds_by_program: defaultdict[str | None, list[float]] = defaultdict(list)
    for stretch in stretches:
        stretch_seconds_by_program[stretch.program].append(stretch.seconds)
    ideal_seconds = sum(
        min(seconds) * len(seconds) for seconds in stretch_seconds_by_program.values()
    )
    return ideal_seconds / active_seconds


def _active_stretches(readings: Sequence[SourceReading]) -> list[_ActiveStretch]:
    """Stretches start at an entry into ACTIVE, the same set NA-PROD-2 counts."""
    stretches: list[_ActiveStretch] = []
    program: str | None = None
    previous_execution: str | None = None
    opened: SourceReading | None = None
    opened_program: str | None = None
    for reading in readings:
        if reading.data_item_name == PROGRAM:
            program = reading.value
        if reading.data_item_name != EXECUTION:
            continue
        if opened is not None and reading.value != ACTIVE:
            stretches.append(_ActiveStretch(opened_program, _seconds_between(opened, reading)))
            opened = None
        if previous_execution not in (None, ACTIVE) and reading.value == ACTIVE:
            opened, opened_program = reading, program
        previous_execution = reading.value
    if opened is not None:
        stretches.append(_ActiveStretch(opened_program, _seconds_between(opened, readings[-1])))
    return stretches


def _seconds_between(opened: SourceReading, closed: SourceReading) -> float:
    return (closed.source_observed_at - opened.source_observed_at).total_seconds()
