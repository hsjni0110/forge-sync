"""Preregistered naive production-count rules (NA-PROD-*)."""

from __future__ import annotations

from collections.abc import Sequence
from itertools import pairwise

from .naive_estimate import (
    ACTIVE,
    EXECUTION,
    NaiveEstimate,
    SourceReading,
    available_integer_values,
)

PART_COUNTER = "PartCountAct"


def estimate_parts_from_part_counter(readings: Sequence[SourceReading]) -> NaiveEstimate:
    counter_values = available_integer_values(readings, PART_COUNTER)
    return NaiveEstimate(
        rule_id="NA-PROD-1",
        value=max(counter_values) - min(counter_values) if counter_values else None,
        hidden_assumptions=("PART_COUNTER_COUNTS_COMPLETED_PARTS",),
    )


def estimate_parts_from_active_entries(readings: Sequence[SourceReading]) -> NaiveEstimate:
    execution_values = [
        reading.value for reading in readings if reading.data_item_name == EXECUTION
    ]
    entries = sum(
        1
        for previous, current in pairwise(execution_values)
        if previous != ACTIVE and current == ACTIVE
    )
    return NaiveEstimate(
        rule_id="NA-PROD-2",
        value=entries,
        hidden_assumptions=("ONE_ACTIVE_ENTRY_IS_ONE_PART",),
    )
