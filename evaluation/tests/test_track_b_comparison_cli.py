from __future__ import annotations

import json
from pathlib import Path

import pytest
from forgesync_evaluation.adapter.inbound.cli import main
from forgesync_evaluation.adapter.outbound.projection_document_files import (
    save_delivery_documents,
)
from forgesync_evaluation.application.forgesync_track_b import DeliveryDocuments


def source(item_id: str) -> dict[str, object]:
    return {"transformation": {"sourceDataItemId": item_id}}


def envelope(item_id: str, observed_at: str, kind: str, payload: dict[str, str]) -> str:
    return json.dumps(
        {
            "observationKind": kind,
            "payload": payload,
            "provenance": source(item_id),
            "source": {"sourceObservedAt": observed_at},
        }
    )


EXECUTION_ACTIVE = envelope(
    "Mazak01-path_13",
    "2016-10-05T09:00:00Z",
    "EVENT",
    {"eventType": "EXECUTION", "value": "ACTIVE"},
)
DOOR_WARNING = envelope(
    "Mazak01-controller_2", "2016-10-05T09:00:30Z", "CONDITION", {"level": "WARNING"}
)
FORGESYNC_DOCUMENTS = DeliveryDocuments(
    twin={
        "state": {"execution": {"value": "ACTIVE", "provenance": [source("Mazak01-path_13")]}},
        "metrics": {
            "toolNumber": {"provenance": source("Mazak01-path_10")},
            "program": {"provenance": source("Mazak01-path_1")},
            "spindleSpeeds": [],
            "axisPositions": [],
        },
    },
    intervals={"intervals": []},
    machining_runs={"machiningRuns": [{}]},
    alarms={"alarms": [{}]},
)


def write_run(directory: Path, delivered: list[str]) -> None:
    save_delivery_documents(FORGESYNC_DOCUMENTS, directory)
    (directory / "deliveries.ndjson").write_text("\n".join(delivered) + "\n", encoding="utf-8")


def test_track_b_reports_naive_side_effects_where_forgesync_stays_on_its_baseline(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    write_run(tmp_path / "S0", [EXECUTION_ACTIVE, DOOR_WARNING])
    write_run(tmp_path / "S1", [EXECUTION_ACTIVE, DOOR_WARNING, DOOR_WARNING])

    exit_code = main(["compare-track-b", "--runs-directory", str(tmp_path)])

    report = json.loads(capsys.readouterr().out)
    assert exit_code == 0
    assert report["scenarios"] == [
        {
            "scenario": "S1",
            "deliveredEnvelopeCount": 3,
            "baselineDeliveredEnvelopeCount": 2,
            "naive": {
                "finalValueMismatchCount": 0,
                "rollbackCount": 0,
                "misattributedSeconds": 0.0,
                "activeEntryDifference": 0,
                "extraAlarmOpeningCount": 1,
            },
            "forgesync": {
                "finalValueMismatchCount": 0,
                "rollbackCount": None,
                "misattributedSeconds": 0.0,
                "activeEntryDifference": 0,
                "extraAlarmOpeningCount": 0,
            },
        }
    ]
