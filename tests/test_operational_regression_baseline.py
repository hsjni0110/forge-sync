from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).parents[1]
BASELINE_PATH = REPOSITORY_ROOT / "tests/fixtures/regression/mazak01-operational-baseline.json"


def test_checked_in_operational_baseline_files_keep_their_bytes() -> None:
    baseline = _load_object(BASELINE_PATH)

    for descriptor in baseline["checkedInFiles"]:
        fixture_path = REPOSITORY_ROOT / descriptor["path"]
        actual_sha256 = hashlib.sha256(fixture_path.read_bytes()).hexdigest()

        assert actual_sha256 == descriptor["sha256"]


def test_operational_twin_keeps_the_frozen_meaning() -> None:
    baseline = _load_object(BASELINE_PATH)
    expected = baseline["expectedOperationalState"]
    snapshot = _load_object(REPOSITORY_ROOT / baseline["checkedInFiles"][0]["path"])
    visual_spindle = next(
        speed
        for speed in snapshot["metrics"]["spindleSpeeds"]
        if speed["provenance"]["transformation"]["sourceDataItemId"]
        == expected["visualSpindleSourceDataItemId"]
    )

    assert snapshot["machine"]["machineId"] == expected["machineId"]
    assert snapshot["consistency"]["twinVersion"] == expected["twinVersion"]
    assert snapshot["state"]["freshness"]["value"] == expected["freshness"]
    assert snapshot["state"]["execution"]["value"] == expected["execution"]
    assert visual_spindle["value"] == expected["rpm"]
    assert snapshot["metrics"]["toolNumber"]["value"] == expected["toolNumber"]
    assert expected["rpm"] / 500 == expected["visualAngularVelocityRadPerSecond"]


def test_e2e_scenario_uses_the_frozen_canonical_output() -> None:
    baseline = _load_object(BASELINE_PATH)
    scenario = _load_object(REPOSITORY_ROOT / "tests/e2e/fixtures/machine-detail-replay.json")

    assert scenario["canonicalRun"] == baseline["canonicalOutput"]["path"]
    assert scenario["canonicalSha256"] == baseline["canonicalOutput"]["sha256"]


def _load_object(path: Path) -> dict[str, Any]:
    value: object = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise TypeError(f"Expected a JSON object: {path}")
    return value
