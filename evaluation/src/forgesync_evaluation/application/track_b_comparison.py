"""Measure each consumer's drift from its own fault-free run, scenario by scenario."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from forgesync_evaluation.domain.delivery_divergence import DeliveryDivergence, diverge
from forgesync_evaluation.domain.naive_stream import ConsumerOutcome


@dataclass(frozen=True, slots=True)
class ScenarioOutcomes:
    scenario: str
    naive: ConsumerOutcome
    forgesync: ConsumerOutcome


@dataclass(frozen=True, slots=True)
class ScenarioDivergence:
    scenario: str
    naive: DeliveryDivergence
    forgesync: DeliveryDivergence


def compare_track_b(
    baseline: ScenarioOutcomes,
    scenarios: Sequence[ScenarioOutcomes],
    tracked_item_ids: Sequence[str],
) -> tuple[ScenarioDivergence, ...]:
    return tuple(
        ScenarioDivergence(
            scenario.scenario,
            naive=diverge(baseline.naive, scenario.naive, tracked_item_ids),
            forgesync=diverge(baseline.forgesync, scenario.forgesync, tracked_item_ids),
        )
        for scenario in scenarios
    )
