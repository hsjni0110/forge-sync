from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from forgesync_evaluation.domain.naive_estimate import SourceReading
from forgesync_evaluation.domain.utilization_estimates import (
    estimate_utilization_from_accumulated_counters,
    estimate_utilization_over_available_time,
    estimate_utilization_over_observed_span,
)

STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def reading(seconds: float, name: str, value: str) -> SourceReading:
    return SourceReading(STARTED_AT + timedelta(seconds=seconds), name, value)


# Observed span 0..100 s. ACTIVE 20..50 and 70..100, UNAVAILABLE 50..70, READY 10..20.
SHIFT_WITH_A_GAP = [
    reading(0, "Srpm", "0"),
    reading(10, "execution", "READY"),
    reading(20, "execution", "ACTIVE"),
    reading(50, "execution", "UNAVAILABLE"),
    reading(70, "execution", "ACTIVE"),
    reading(100, "Srpm", "1200"),
]


def test_span_rule_divides_active_dwell_by_the_whole_observed_span() -> None:
    estimate = estimate_utilization_over_observed_span(SHIFT_WITH_A_GAP)

    assert estimate.rule_id == "NA-UTIL-1"
    assert estimate.value == pytest.approx(0.6)
    assert estimate.hidden_assumptions == ("UNAVAILABLE_MEANS_NOT_OPERATING",)


def test_span_rule_has_no_value_when_the_observed_span_is_empty() -> None:
    readings = [reading(5, "execution", "ACTIVE")]

    assert estimate_utilization_over_observed_span(readings).value is None


def test_available_time_rule_drops_unavailable_dwell_from_the_denominator() -> None:
    estimate = estimate_utilization_over_available_time(SHIFT_WITH_A_GAP)

    assert estimate.rule_id == "NA-UTIL-2"
    assert estimate.value == pytest.approx(60 / 70)
    assert estimate.hidden_assumptions == ("MISSING_TIME_MATCHES_OBSERVED_TIME",)


def test_available_time_rule_has_no_value_when_execution_is_never_available() -> None:
    readings = [reading(0, "execution", "UNAVAILABLE"), reading(30, "Srpm", "0")]

    assert estimate_utilization_over_available_time(readings).value is None


def test_counter_rule_divides_automatic_counter_growth_by_total_counter_growth() -> None:
    readings = [
        reading(0, "total_time", "UNAVAILABLE"),
        reading(1, "total_time", "1000"),
        reading(1, "auto_time", "500"),
        reading(60, "auto_time", "530"),
        reading(80, "total_time", "1080"),
        reading(90, "auto_time", "UNAVAILABLE"),
    ]

    estimate = estimate_utilization_from_accumulated_counters(readings)

    assert estimate.rule_id == "NA-UTIL-3"
    assert estimate.value == pytest.approx(30 / 80)
    assert estimate.hidden_assumptions == ("COUNTERS_SHARE_UNIT_AND_MEANING",)


def test_counter_rule_has_no_value_when_the_total_counter_does_not_grow() -> None:
    readings = [
        reading(0, "total_time", "1000"),
        reading(0, "auto_time", "500"),
        reading(9, "total_time", "1000"),
    ]

    assert estimate_utilization_from_accumulated_counters(readings).value is None
