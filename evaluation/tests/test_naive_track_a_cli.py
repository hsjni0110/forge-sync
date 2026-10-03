from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest
from forgesync_evaluation.adapter.inbound.cli import main

PAYLOAD = (
    b"2016-10-05T09:00:00Z|PartCountAct|0\n"
    b"2016-10-05T09:00:00Z|total_time|1000\n"
    b"2016-10-05T09:00:00Z|auto_time|500\n"
    b"2016-10-05T09:00:10Z|execution|READY\n"
    b"2016-10-05T09:00:20Z|execution|ACTIVE\n"
    b"2016-10-05T09:00:50Z|execution|UNAVAILABLE\n"
    b"2016-10-05T09:01:10Z|execution|ACTIVE\n"
    b"2016-10-05T09:01:40Z|auto_time|560\n"
    b"2016-10-05T09:01:40Z|total_time|1100\n"
    b"2016-10-05T09:01:40Z|PartCountAct|0\n"
)


def test_naive_track_a_prints_every_preregistered_estimate_with_the_payload_checksum(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    payload = tmp_path / "payload"
    payload.write_bytes(PAYLOAD)

    exit_code = main(["naive-track-a", "--raw-payload", str(payload)])

    report = json.loads(capsys.readouterr().out)
    assert exit_code == 0
    assert report["rawPayloadSha256"] == hashlib.sha256(PAYLOAD).hexdigest()
    assert [(item["ruleId"], item["value"]) for item in report["estimates"]] == [
        ("NA-PROD-1", 0),
        ("NA-PROD-2", 2),
        ("NA-UTIL-1", pytest.approx(60 / 100)),
        ("NA-UTIL-2", pytest.approx(60 / 70)),
        ("NA-UTIL-3", pytest.approx(60 / 100)),
        ("NA-OEE-1", pytest.approx(60 / 100 * 1.0)),
        ("NA-OEE-2", pytest.approx(60 / 70 * 1.0)),
    ]
    assert report["estimates"][0]["hiddenAssumptions"] == ["PART_COUNTER_COUNTS_COMPLETED_PARTS"]
