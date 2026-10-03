from __future__ import annotations

from datetime import UTC, datetime, timedelta

from forgesync_evaluation.domain.naive_stream import (
    DeliveredObservation,
    TimelineSegment,
    consume_naively,
)

EXECUTION = "Mazak01-path_13"
STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def at(seconds: float) -> datetime:
    return STARTED_AT + timedelta(seconds=seconds)


def execution(seconds: float, value: str) -> DeliveredObservation:
    return DeliveredObservation(EXECUTION, at(seconds), value, is_non_normal_condition=False)


DOOR_WARNING = DeliveredObservation("Mazak01-controller_2", at(25), None, True)


def test_in_order_stream_keeps_latest_values_and_source_timed_execution_segments() -> None:
    outcome = consume_naively(
        [execution(0, "READY"), execution(10, "ACTIVE"), DOOR_WARNING, execution(40, "READY")],
        EXECUTION,
    )

    assert outcome.final_values == {EXECUTION: "READY", "Mazak01-controller_2": None}
    assert outcome.rollback_count == 0
    assert outcome.active_entry_count == 1
    assert outcome.alarm_opening_count == 1
    assert outcome.execution_timeline == (
        TimelineSegment(at(0), at(10), "READY"),
        TimelineSegment(at(10), at(40), "ACTIVE"),
        TimelineSegment(at(40), at(40), "READY"),
    )


def test_late_arrival_rolls_the_current_value_back_and_shrinks_segments_to_zero() -> None:
    outcome = consume_naively(
        [execution(0, "READY"), execution(40, "READY"), execution(10, "ACTIVE")], EXECUTION
    )

    assert outcome.final_values[EXECUTION] == "ACTIVE"
    assert outcome.rollback_count == 1
    assert outcome.execution_timeline == (
        TimelineSegment(at(0), at(40), "READY"),
        TimelineSegment(at(40), at(40), "READY"),
        TimelineSegment(at(10), at(10), "ACTIVE"),
    )


def test_redelivered_condition_opens_another_alarm_without_another_active_entry() -> None:
    outcome = consume_naively(
        [execution(0, "READY"), execution(10, "ACTIVE"), execution(10, "ACTIVE")]
        + [DOOR_WARNING, DOOR_WARNING],
        EXECUTION,
    )

    assert outcome.active_entry_count == 1
    assert outcome.alarm_opening_count == 2
