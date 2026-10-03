"""Read what ForgeSync ended with after one replay, in the shape the naive consumer reports."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from forgesync_evaluation.domain.naive_stream import ConsumerOutcome, TimelineSegment

JsonDocument = dict[str, Any]


def read_forgesync_outcome(
    twin: JsonDocument,
    intervals: JsonDocument,
    machining_runs: JsonDocument,
    alarms: JsonDocument,
) -> ConsumerOutcome:
    return ConsumerOutcome(
        final_values=_final_values(twin),
        # The REST surface exposes no history of Twin changes, so rollbacks are not observable.
        rollback_count=None,
        execution_timeline=tuple(
            TimelineSegment(
                datetime.fromisoformat(interval["startedAt"]),
                # ADR-050 leaves the final interval open; it claims no duration here.
                datetime.fromisoformat(interval.get("endedAt", interval["startedAt"])),
                # UNAVAILABLE intervals carry no value, matching the naive consumer's None.
                interval.get("value"),
            )
            for interval in intervals["intervals"]
            if interval["signal"] == "EXECUTION"
        ),
        active_entry_count=len(machining_runs["machiningRuns"]),
        alarm_opening_count=len(alarms["alarms"]),
    )


def _final_values(twin: JsonDocument) -> dict[str, str | None]:
    metrics = twin["metrics"]
    components = [
        twin["state"]["execution"],
        metrics["toolNumber"],
        metrics["program"],
        *metrics["spindleSpeeds"],
        *metrics["axisPositions"],
    ]
    return {_source_item_id(component): _value(component) for component in components}


def _source_item_id(component: JsonDocument) -> str:
    provenance = component["provenance"]
    # State fields carry a provenance list, metric fields a single provenance object.
    first = provenance[0] if isinstance(provenance, list) else provenance
    return str(first["transformation"]["sourceDataItemId"])


def _value(component: JsonDocument) -> str | None:
    value = component.get("value")
    return None if value is None else str(value)
