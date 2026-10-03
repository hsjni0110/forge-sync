"""What ForgeSync discloses for the same Track A questions the naive rules answer."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from .stop_cause_estimates import DowntimeInterval


@dataclass(frozen=True, slots=True)
class DisclosedValue:
    """`percent` is None whenever ForgeSync declined to produce a number; `reason` says why."""

    status: str
    percent: float | None
    reason: str | None


@dataclass(frozen=True, slots=True)
class ForgeSyncDisclosure:
    production_result_status: str
    part_count: DisclosedValue
    machining_run_count: int
    completed_machining_run_count: int
    active_state: DisclosedValue
    unknown_state_percent: float
    counter_automatic: DisclosedValue
    composite_oee: DisclosedValue
    performance: DisclosedValue
    downtimes: tuple[DowntimeInterval, ...]
    downtime_reason_counts: Mapping[str, int]
