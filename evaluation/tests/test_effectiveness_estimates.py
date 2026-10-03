from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from forgesync_evaluation.domain.effectiveness_estimates import (
    estimate_effectiveness_over_available_time,
    estimate_effectiveness_over_observed_span,
)
from forgesync_evaluation.domain.naive_estimate import SourceReading

STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def reading(seconds: float, name: str, value: str) -> SourceReading:
    return SourceReading(STARTED_AT + timedelta(seconds=seconds), name, value)


# Program 155 has ACTIVE stretches of 10 s and 20 s, program 114 one of 30 s.
# Ideal work = 10 * 2 + 30 * 1 = 50 s over 60 s of ACTIVE dwell.
TWO_PROGRAM_SHIFT = [
    reading(0, "program", "155"),
    reading(0, "execution", "READY"),
    reading(10, "execution", "ACTIVE"),
    reading(20, "execution", "READY"),
    reading(30, "execution", "ACTIVE"),
    reading(50, "execution", "READY"),
    reading(55, "program", "114"),
    reading(60, "execution", "ACTIVE"),
    reading(90, "execution", "UNAVAILABLE"),
    reading(100, "Srpm", "0"),
]


def test_span_oee_multiplies_span_utilization_by_shortest_stretch_performance() -> None:
    estimate = estimate_effectiveness_over_observed_span(TWO_PROGRAM_SHIFT)

    assert estimate.rule_id == "NA-OEE-1"
    assert estimate.value == pytest.approx(0.6 * 50 / 60)
    assert estimate.hidden_assumptions == (
        "UNAVAILABLE_MEANS_NOT_OPERATING",
        "OBSERVED_SPAN_IS_PLANNED_PRODUCTION_TIME",
        "ONE_ACTIVE_ENTRY_IS_ONE_PART",
        "SHORTEST_ACTIVE_STRETCH_IS_IDEAL_CYCLE",
        "NO_QUALITY_DATA_MEANS_ALL_GOOD",
    )


def test_span_oee_has_no_value_without_an_entry_into_active() -> None:
    readings = [reading(0, "execution", "ACTIVE"), reading(30, "execution", "READY")]

    assert estimate_effectiveness_over_observed_span(readings).value is None


def test_available_time_oee_uses_available_time_utilization_and_its_assumptions() -> None:
    estimate = estimate_effectiveness_over_available_time(TWO_PROGRAM_SHIFT)

    assert estimate.rule_id == "NA-OEE-2"
    assert estimate.value == pytest.approx(60 / 90 * 50 / 60)
    assert estimate.hidden_assumptions == (
        "MISSING_TIME_MATCHES_OBSERVED_TIME",
        "AVAILABLE_TIME_IS_PLANNED_PRODUCTION_TIME",
        "ONE_ACTIVE_ENTRY_IS_ONE_PART",
        "SHORTEST_ACTIVE_STRETCH_IS_IDEAL_CYCLE",
        "NO_QUALITY_DATA_MEANS_ALL_GOOD",
    )


def test_span_oee_closes_a_stretch_still_active_at_the_last_reading() -> None:
    readings = [
        reading(0, "program", "155"),
        reading(0, "execution", "READY"),
        reading(10, "execution", "ACTIVE"),
        reading(20, "execution", "READY"),
        reading(40, "execution", "ACTIVE"),
        reading(70, "Srpm", "900"),
    ]

    estimate = estimate_effectiveness_over_observed_span(readings)

    assert estimate.value == pytest.approx(40 / 70 * (10 * 2) / 40)
