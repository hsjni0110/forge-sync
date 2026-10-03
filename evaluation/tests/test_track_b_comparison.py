from __future__ import annotations

from forgesync_evaluation.application.track_b_comparison import (
    ScenarioDivergence,
    ScenarioOutcomes,
    compare_track_b,
)
from forgesync_evaluation.domain.delivery_divergence import DeliveryDivergence
from forgesync_evaluation.domain.naive_stream import ConsumerOutcome


def outcome(rollbacks: int | None, entries: int, alarms: int) -> ConsumerOutcome:
    return ConsumerOutcome({"x": "1"}, rollbacks, (), entries, alarms)


def test_each_consumer_is_measured_against_its_own_fault_free_run() -> None:
    baseline = ScenarioOutcomes("S0", naive=outcome(0, 10, 4), forgesync=outcome(None, 8, 3))
    faulted = ScenarioOutcomes("S2", naive=outcome(5, 12, 9), forgesync=outcome(None, 8, 3))

    divergences = compare_track_b(baseline, [faulted], tracked_item_ids=("x",))

    assert divergences == (
        ScenarioDivergence(
            "S2",
            naive=DeliveryDivergence(0, 5, 0.0, 2, 5),
            forgesync=DeliveryDivergence(0, None, 0.0, 0, 0),
        ),
    )
