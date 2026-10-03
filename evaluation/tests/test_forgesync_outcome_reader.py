from __future__ import annotations

from datetime import UTC, datetime

from forgesync_evaluation.adapter.outbound.forgesync_outcome_reader import read_forgesync_outcome
from forgesync_evaluation.domain.naive_stream import ConsumerOutcome, TimelineSegment


def source(item_id: str) -> dict[str, object]:
    return {"transformation": {"sourceDataItemId": item_id}}


# DERIVED_FIXTURE: shaped like the 2026-10-03 exploration responses, values reduced.
TWIN = {
    "state": {"execution": {"value": "READY", "provenance": [source("Mazak01-path_13")]}},
    "metrics": {
        "toolNumber": {"value": "12", "provenance": source("Mazak01-path_10")},
        "program": {"availability": "UNAVAILABLE", "provenance": source("Mazak01-path_1")},
        "spindleSpeeds": [
            {"value": 1200.0, "provenance": source("Mazak01-C_5")},
            {"value": 0.0, "provenance": source("Mazak01-C2_3")},
        ],
        "axisPositions": [{"axis": "X", "value": -1.5, "provenance": source("Mazak01-X_1")}],
    },
}
INTERVALS = {
    "intervals": [
        {
            "signal": "CONTROLLER_MODE",
            "value": "AUTOMATIC",
            "startedAt": "2016-10-05T09:00:00Z",
            "endedAt": "2016-10-05T09:10:00Z",
        },
        {
            "signal": "EXECUTION",
            "value": "ACTIVE",
            "startedAt": "2016-10-05T09:00:00Z",
            "endedAt": "2016-10-05T09:01:00Z",
        },
    ]
}


def test_forgesync_outcome_uses_source_item_keys_and_leaves_rollbacks_unobserved() -> None:
    outcome = read_forgesync_outcome(
        TWIN,
        INTERVALS,
        machining_runs={"machiningRuns": [{}, {}]},
        alarms={"alarms": [{}]},
    )

    assert outcome == ConsumerOutcome(
        final_values={
            "Mazak01-path_13": "READY",
            "Mazak01-path_10": "12",
            "Mazak01-path_1": None,
            "Mazak01-C_5": "1200.0",
            "Mazak01-C2_3": "0.0",
            "Mazak01-X_1": "-1.5",
        },
        rollback_count=None,
        execution_timeline=(
            TimelineSegment(
                datetime(2016, 10, 5, 9, 0, tzinfo=UTC),
                datetime(2016, 10, 5, 9, 1, tzinfo=UTC),
                "ACTIVE",
            ),
        ),
        active_entry_count=2,
        alarm_opening_count=1,
    )


def test_unavailable_execution_interval_without_a_value_becomes_an_unknown_segment() -> None:
    intervals = {
        "intervals": [
            {
                "signal": "EXECUTION",
                "startedAt": "2016-10-05T05:27:55.740706Z",
                "endedAt": "2016-10-05T08:43:49.514Z",
            }
        ]
    }

    outcome = read_forgesync_outcome(TWIN, intervals, {"machiningRuns": []}, {"alarms": []})

    assert outcome.execution_timeline == (
        TimelineSegment(
            datetime(2016, 10, 5, 5, 27, 55, 740706, tzinfo=UTC),
            datetime(2016, 10, 5, 8, 43, 49, 514000, tzinfo=UTC),
            None,
        ),
    )


def test_open_final_interval_claims_no_duration() -> None:
    intervals = {
        "intervals": [
            {"signal": "EXECUTION", "value": "READY", "startedAt": "2016-10-05T19:14:00Z"}
        ]
    }

    outcome = read_forgesync_outcome(TWIN, intervals, {"machiningRuns": []}, {"alarms": []})

    opened = datetime(2016, 10, 5, 19, 14, tzinfo=UTC)
    assert outcome.execution_timeline == (TimelineSegment(opened, opened, "READY"),)
