"""Read envelopes in the order the broker delivered them to the capturing subscriber."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any

from forgesync_evaluation.domain.naive_stream import DeliveredObservation

NON_NORMAL_CONDITION_LEVELS = frozenset({"WARNING", "FAULT"})


def read_delivery_log(log_path: Path) -> list[DeliveredObservation]:
    with log_path.open(encoding="utf-8") as log:
        return [_delivered(json.loads(line)) for line in log if line.strip()]


def _delivered(envelope: dict[str, Any]) -> DeliveredObservation:
    payload = envelope["payload"]
    is_condition = envelope["observationKind"] == "CONDITION"
    # A condition's state is its level; events and samples omit `value` when UNAVAILABLE.
    value = payload.get("level") if is_condition else payload.get("value")
    return DeliveredObservation(
        source_data_item_id=envelope["provenance"]["transformation"]["sourceDataItemId"],
        source_observed_at=datetime.fromisoformat(envelope["source"]["sourceObservedAt"]),
        value=None if value is None else str(value),
        is_non_normal_condition=is_condition and value in NON_NORMAL_CONDITION_LEVELS,
    )
