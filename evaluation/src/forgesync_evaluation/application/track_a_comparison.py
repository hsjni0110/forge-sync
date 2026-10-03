"""Put naive estimates and ForgeSync disclosures for the same source side by side."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from forgesync_evaluation.application.naive_track_a import estimate_naive_track_a
from forgesync_evaluation.domain.forgesync_disclosure import ForgeSyncDisclosure
from forgesync_evaluation.domain.naive_estimate import NaiveEstimate, SourceReading
from forgesync_evaluation.domain.stop_cause_estimates import estimate_stop_causes


@dataclass(frozen=True, slots=True)
class TrackAComparison:
    naive_estimates: tuple[NaiveEstimate, ...]
    forgesync: ForgeSyncDisclosure


def compare_track_a(
    readings: Sequence[SourceReading], disclosure: ForgeSyncDisclosure
) -> TrackAComparison:
    # NA-CAUSE-1 is preregistered to reuse ForgeSync's downtime intervals as its stop list.
    return TrackAComparison(
        naive_estimates=(
            *estimate_naive_track_a(readings),
            estimate_stop_causes(disclosure.downtimes, readings),
        ),
        forgesync=disclosure,
    )
