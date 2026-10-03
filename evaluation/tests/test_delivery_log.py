from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from forgesync_evaluation.adapter.outbound.delivery_log import read_delivery_log
from forgesync_evaluation.domain.naive_stream import DeliveredObservation


def envelope(item_id: str, observed_at: str, kind: str, payload: dict[str, Any]) -> str:
    return json.dumps(
        {
            "observationKind": kind,
            "payload": payload,
            "provenance": {"transformation": {"sourceDataItemId": item_id}},
            "source": {"sourceObservedAt": observed_at},
            "replay": {"replaySequence": 7},
        }
    )


def test_log_keeps_arrival_order_values_and_marks_warning_conditions(tmp_path: Path) -> None:
    log = tmp_path / "deliveries.ndjson"
    log.write_text(
        "\n".join(
            [
                envelope(
                    "Mazak01-controller_3",
                    "2016-10-05T09:03:57.872Z",
                    "CONDITION",
                    {"level": "WARNING", "nativeCode": "406"},
                ),
                envelope(
                    "Mazak01-path_13",
                    "2016-10-05T09:01:37.891Z",
                    "EVENT",
                    {"eventType": "EXECUTION", "value": "ACTIVE"},
                ),
                envelope(
                    "Mazak01-C_5",
                    "2016-10-05T05:27:55.740706Z",
                    "SAMPLE",
                    {"availability": "UNAVAILABLE", "metric": "SPINDLE_SPEED"},
                ),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    assert read_delivery_log(log) == [
        DeliveredObservation(
            "Mazak01-controller_3",
            datetime(2016, 10, 5, 9, 3, 57, 872000, tzinfo=UTC),
            "WARNING",
            is_non_normal_condition=True,
        ),
        DeliveredObservation(
            "Mazak01-path_13",
            datetime(2016, 10, 5, 9, 1, 37, 891000, tzinfo=UTC),
            "ACTIVE",
            is_non_normal_condition=False,
        ),
        DeliveredObservation(
            "Mazak01-C_5",
            datetime(2016, 10, 5, 5, 27, 55, 740706, tzinfo=UTC),
            None,
            is_non_normal_condition=False,
        ),
    ]
