from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from forgesync_evaluation.domain.delivery_divergence import (
    DeliveryDivergence,
    diverge,
    misattributed_seconds,
)
from forgesync_evaluation.domain.naive_stream import ConsumerOutcome, TimelineSegment

STARTED_AT = datetime(2016, 10, 5, 9, 0, tzinfo=UTC)


def segment(start: float, end: float, value: str) -> TimelineSegment:
    return TimelineSegment(
        STARTED_AT + timedelta(seconds=start), STARTED_AT + timedelta(seconds=end), value
    )


BASELINE = (segment(0, 10, "READY"), segment(10, 40, "ACTIVE"))


def test_time_counts_where_the_observed_state_differs_from_the_baseline() -> None:
    observed = (segment(0, 40, "READY"), segment(40, 40, "READY"), segment(10, 10, "ACTIVE"))

    assert misattributed_seconds(BASELINE, observed) == pytest.approx(30.0)


def test_the_latest_arrived_segment_wins_where_observed_segments_overlap() -> None:
    baseline = (segment(0, 10, "READY"), segment(10, 30, "ACTIVE"), segment(30, 40, "READY"))
    observed = (segment(0, 40, "READY"), segment(10, 30, "ACTIVE"))

    assert misattributed_seconds(baseline, observed) == 0.0


def test_divergence_compares_tracked_final_values_and_counts_against_the_baseline() -> None:
    baseline = ConsumerOutcome({"x": "1", "y": "A", "z": "q"}, 0, BASELINE, 3, 4)
    observed = ConsumerOutcome({"x": "1", "y": "B", "z": "r"}, 2, BASELINE, 5, 9)

    assert diverge(baseline, observed, tracked_item_ids=("x", "y")) == DeliveryDivergence(
        final_value_mismatch_count=1,
        rollback_count=2,
        misattributed_seconds=0.0,
        active_entry_difference=2,
        extra_alarm_opening_count=5,
    )
