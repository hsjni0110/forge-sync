from __future__ import annotations

from datetime import UTC, datetime, timedelta

from forgesync_evaluation.domain.naive_estimate import SourceReading
from forgesync_evaluation.domain.stop_cause_estimates import (
    DowntimeInterval,
    estimate_stop_causes,
)

STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def at(seconds: float) -> datetime:
    return STARTED_AT + timedelta(seconds=seconds)


def reading(seconds: float, name: str, value: str) -> SourceReading:
    return SourceReading(at(seconds), name, value)


def test_cause_rule_counts_stops_with_a_warning_or_fault_inside_the_half_open_interval() -> None:
    downtimes = [
        DowntimeInterval(at(100), at(200)),
        DowntimeInterval(at(300), at(400)),
        DowntimeInterval(at(500), at(600)),
    ]
    readings = [
        reading(150, "logic_cond", "Warning|345|||ERROR(DOOR OPEN)"),
        reading(180, "system_cond", "Fault|1101|||INTERFERE"),
        reading(350, "logic_cond", "Normal||||"),
        reading(400, "system_cond", "Warning|406|||MEMORY PROTECT"),
        reading(499, "logic_cond", "Warning|345|||ERROR(DOOR OPEN)"),
        reading(550, "execution", "STOPPED"),
    ]

    estimate = estimate_stop_causes(downtimes, readings)

    assert estimate.rule_id == "NA-CAUSE-1"
    assert estimate.value == 1
    assert estimate.hidden_assumptions == ("CONCURRENT_CONDITION_IS_CAUSE",)


def test_cause_rule_reports_zero_without_downtime() -> None:
    readings = [reading(10, "logic_cond", "Warning|345|||ERROR(DOOR OPEN)")]

    assert estimate_stop_causes([], readings).value == 0
