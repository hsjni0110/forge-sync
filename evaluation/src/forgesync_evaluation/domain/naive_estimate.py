"""Values a preregistered naive dashboard rule would show, with the assumptions it hides."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

UNAVAILABLE = "UNAVAILABLE"
EXECUTION = "execution"
ACTIVE = "ACTIVE"


@dataclass(frozen=True, slots=True)
class SourceReading:
    """One source line as a naive dashboard reads it: time, name and untyped text."""

    source_observed_at: datetime
    data_item_name: str
    value: str

    @property
    def is_available(self) -> bool:
        return self.value != UNAVAILABLE


@dataclass(frozen=True, slots=True)
class NaiveEstimate:
    """`value` is None only where even the naive rule cannot produce a number."""

    rule_id: str
    value: float | None
    hidden_assumptions: tuple[str, ...]


def available_integer_values(readings: Sequence[SourceReading], data_item_name: str) -> list[int]:
    return [
        int(reading.value)
        for reading in readings
        if reading.data_item_name == data_item_name and reading.is_available
    ]
