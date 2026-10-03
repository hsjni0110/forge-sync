from __future__ import annotations

from datetime import UTC, datetime

from forgesync_evaluation.adapter.outbound.projection_document_reader import read_disclosure
from forgesync_evaluation.application.forgesync_track_a import ProjectionDocuments
from forgesync_evaluation.domain.forgesync_disclosure import DisclosedValue, ForgeSyncDisclosure
from forgesync_evaluation.domain.stop_cause_estimates import DowntimeInterval

# DERIVED_FIXTURE: values copied from the 2026-10-03 exploration replay of Mazak01
# (source set nist-mazak01-20161005, cursor 101679), reduced to the fields the reader uses.
# Runs and downtime entries are truncated to a few representative items.
DOCUMENTS = ProjectionDocuments(
    machining_runs={
        "machiningRuns": [{"status": "COMPLETED"}, {"status": "UNKNOWN"}, {"status": "COMPLETED"}]
    },
    utilization={
        "state": {
            "status": "AVAILABLE",
            "states": [
                {"state": "ACTIVE", "ratioPercent": 23.471187},
                {"state": "UNKNOWN", "ratioPercent": 25.498173},
            ],
        },
        "counters": {"automaticRatio": {"status": "PARTIAL", "ratioPercent": 31.561114}},
    },
    downtime_pareto={
        "entries": [
            {
                "startedAt": "2016-10-05T05:27:55.740706Z",
                "endedAt": "2016-10-05T08:43:49.514Z",
                "reasonClassification": "UNCONFIRMED_REASON",
            },
            {
                "startedAt": "2016-10-05T09:01:54.244Z",
                "endedAt": "2016-10-05T09:06:47.276Z",
                "reasonClassification": "CONCURRENT_EVIDENCE",
            },
        ]
    },
    effectiveness={
        "performance": {"status": "AVAILABLE", "percent": 756.95846},
        "compositeOee": {"status": "UNAVAILABLE", "reason": "QUALITY_COMPONENT_UNAVAILABLE"},
    },
    production_context={
        "productionResultStatus": "NOT_OBSERVED",
        "partCount": {"status": "UNAVAILABLE", "reason": "NO_USABLE_TRANSITIONS"},
    },
)


def test_reader_keeps_every_value_forgesync_declined_together_with_its_reason() -> None:
    disclosure = read_disclosure(DOCUMENTS)

    assert disclosure == ForgeSyncDisclosure(
        production_result_status="NOT_OBSERVED",
        part_count=DisclosedValue("UNAVAILABLE", None, "NO_USABLE_TRANSITIONS"),
        machining_run_count=3,
        completed_machining_run_count=2,
        active_state=DisclosedValue("AVAILABLE", 23.471187, None),
        unknown_state_percent=25.498173,
        counter_automatic=DisclosedValue("PARTIAL", 31.561114, None),
        composite_oee=DisclosedValue("UNAVAILABLE", None, "QUALITY_COMPONENT_UNAVAILABLE"),
        performance=DisclosedValue("AVAILABLE", 756.95846, None),
        downtimes=(
            DowntimeInterval(
                datetime(2016, 10, 5, 5, 27, 55, 740706, tzinfo=UTC),
                datetime(2016, 10, 5, 8, 43, 49, 514000, tzinfo=UTC),
            ),
            DowntimeInterval(
                datetime(2016, 10, 5, 9, 1, 54, 244000, tzinfo=UTC),
                datetime(2016, 10, 5, 9, 6, 47, 276000, tzinfo=UTC),
            ),
        ),
        downtime_reason_counts={"UNCONFIRMED_REASON": 1, "CONCURRENT_EVIDENCE": 1},
    )
