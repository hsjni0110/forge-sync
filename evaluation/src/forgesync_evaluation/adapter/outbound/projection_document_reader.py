"""Translate ForgeSync projection documents into the disclosure the comparison reads."""

from __future__ import annotations

from collections import Counter
from datetime import datetime
from typing import Any

from forgesync_evaluation.application.forgesync_track_a import ProjectionDocuments
from forgesync_evaluation.domain.forgesync_disclosure import DisclosedValue, ForgeSyncDisclosure
from forgesync_evaluation.domain.stop_cause_estimates import DowntimeInterval


def read_disclosure(documents: ProjectionDocuments) -> ForgeSyncDisclosure:
    run_statuses = [run["status"] for run in documents.machining_runs["machiningRuns"]]
    state = documents.utilization["state"]
    state_percents = {item["state"]: item["ratioPercent"] for item in state["states"]}
    downtime_entries = documents.downtime_pareto["entries"]
    return ForgeSyncDisclosure(
        production_result_status=documents.production_context["productionResultStatus"],
        part_count=_disclosed(documents.production_context["partCount"]),
        machining_run_count=len(run_statuses),
        completed_machining_run_count=run_statuses.count("COMPLETED"),
        active_state=DisclosedValue(state["status"], state_percents.get("ACTIVE"), None),
        unknown_state_percent=state_percents.get("UNKNOWN", 0.0),
        counter_automatic=_disclosed(documents.utilization["counters"]["automaticRatio"]),
        composite_oee=_disclosed(documents.effectiveness["compositeOee"]),
        performance=_disclosed(documents.effectiveness["performance"]),
        downtimes=tuple(
            DowntimeInterval(_instant(entry["startedAt"]), _instant(entry["endedAt"]))
            for entry in downtime_entries
        ),
        downtime_reason_counts=dict(
            Counter(entry["reasonClassification"] for entry in downtime_entries)
        ),
    )


def _disclosed(component: dict[str, Any]) -> DisclosedValue:
    """Contracts name the number `percent` or `ratioPercent` depending on the projection."""
    percent = component.get("percent", component.get("ratioPercent"))
    return DisclosedValue(component["status"], percent, component.get("reason"))


def _instant(value: str) -> datetime:
    return datetime.fromisoformat(value)
