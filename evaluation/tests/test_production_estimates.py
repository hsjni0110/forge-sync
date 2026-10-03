from __future__ import annotations

from datetime import UTC, datetime, timedelta

from forgesync_evaluation.domain.naive_estimate import SourceReading
from forgesync_evaluation.domain.production_estimates import (
    estimate_parts_from_active_entries,
    estimate_parts_from_part_counter,
)

STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def reading(seconds: float, name: str, value: str) -> SourceReading:
    return SourceReading(STARTED_AT + timedelta(seconds=seconds), name, value)


def test_part_counter_rule_reports_the_range_of_available_counter_values() -> None:
    readings = [
        reading(0, "PartCountAct", "3"),
        reading(1, "PartCountAct", "UNAVAILABLE"),
        reading(2, "execution", "ACTIVE"),
        reading(3, "PartCountAct", "5"),
        reading(4, "PartCountAct", "9"),
    ]

    estimate = estimate_parts_from_part_counter(readings)

    assert estimate.rule_id == "NA-PROD-1"
    assert estimate.value == 6
    assert estimate.hidden_assumptions == ("PART_COUNTER_COUNTS_COMPLETED_PARTS",)


def test_part_counter_rule_reports_zero_when_the_counter_never_moves() -> None:
    readings = [reading(0, "PartCountAct", "0"), reading(9, "PartCountAct", "0")]

    assert estimate_parts_from_part_counter(readings).value == 0


def test_part_counter_rule_has_no_value_without_an_available_counter() -> None:
    readings = [reading(0, "PartCountAct", "UNAVAILABLE"), reading(1, "execution", "READY")]

    assert estimate_parts_from_part_counter(readings).value is None


def test_active_entry_rule_counts_each_change_into_active_in_arrival_order() -> None:
    readings = [
        reading(0, "execution", "READY"),
        reading(1, "execution", "ACTIVE"),
        reading(2, "execution", "ACTIVE"),
        reading(3, "Srpm", "1200"),
        reading(4, "execution", "FEED_HOLD"),
        reading(5, "execution", "ACTIVE"),
        reading(6, "execution", "UNAVAILABLE"),
        reading(7, "execution", "ACTIVE"),
    ]

    estimate = estimate_parts_from_active_entries(readings)

    assert estimate.rule_id == "NA-PROD-2"
    assert estimate.value == 3
    assert estimate.hidden_assumptions == ("ONE_ACTIVE_ENTRY_IS_ONE_PART",)


def test_active_entry_rule_does_not_count_a_first_observation_that_is_already_active() -> None:
    readings = [reading(0, "execution", "ACTIVE"), reading(1, "execution", "READY")]

    assert estimate_parts_from_active_entries(readings).value == 0
