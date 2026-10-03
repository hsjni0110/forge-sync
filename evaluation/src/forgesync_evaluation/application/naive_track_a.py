"""Run every preregistered Track A naive rule over one source."""

from __future__ import annotations

from collections.abc import Callable, Sequence

from forgesync_evaluation.domain.naive_estimate import NaiveEstimate, SourceReading
from forgesync_evaluation.domain.production_estimates import (
    estimate_parts_from_active_entries,
    estimate_parts_from_part_counter,
)
from forgesync_evaluation.domain.utilization_estimates import (
    estimate_utilization_from_accumulated_counters,
    estimate_utilization_over_available_time,
    estimate_utilization_over_observed_span,
)

NaiveRule = Callable[[Sequence[SourceReading]], NaiveEstimate]

# Order follows the preregistration table so reports stay comparable across runs.
PREREGISTERED_RULES: tuple[NaiveRule, ...] = (
    estimate_parts_from_part_counter,
    estimate_parts_from_active_entries,
    estimate_utilization_over_observed_span,
    estimate_utilization_over_available_time,
    estimate_utilization_from_accumulated_counters,
)


def estimate_naive_track_a(readings: Sequence[SourceReading]) -> tuple[NaiveEstimate, ...]:
    return tuple(rule(readings) for rule in PREREGISTERED_RULES)
