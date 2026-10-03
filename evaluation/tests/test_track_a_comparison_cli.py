from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest
from forgesync_evaluation.adapter.inbound.cli import main
from forgesync_evaluation.adapter.outbound.projection_document_files import (
    save_projection_documents,
)
from forgesync_evaluation.application.forgesync_track_a import ProjectionDocuments

PAYLOAD = (
    b"2016-10-05T09:00:00Z|execution|READY\n"
    b"2016-10-05T09:00:10Z|execution|ACTIVE\n"
    b"2016-10-05T09:00:40Z|execution|STOPPED\n"
    b"2016-10-05T09:00:50Z|logic_cond|Warning|345|||ERROR(DOOR OPEN)\n"
    b"2016-10-05T09:01:40Z|execution|READY\n"
)

DOCUMENTS = ProjectionDocuments(
    machining_runs={"machiningRuns": [{"status": "COMPLETED"}]},
    utilization={
        "state": {"status": "AVAILABLE", "states": [{"state": "ACTIVE", "ratioPercent": 30.0}]},
        "counters": {"automaticRatio": {"status": "UNAVAILABLE", "reason": "NO_USABLE_DELTAS"}},
    },
    downtime_pareto={
        "entries": [
            {
                "startedAt": "2016-10-05T09:00:40Z",
                "endedAt": "2016-10-05T09:01:40Z",
                "reasonClassification": "CONCURRENT_EVIDENCE",
            }
        ]
    },
    effectiveness={
        "performance": {"status": "UNAVAILABLE", "reason": "NO_REFERENCE_CYCLE"},
        "compositeOee": {"status": "UNAVAILABLE", "reason": "QUALITY_COMPONENT_UNAVAILABLE"},
    },
    production_context={
        "productionResultStatus": "NOT_OBSERVED",
        "partCount": {"status": "UNAVAILABLE", "reason": "NO_USABLE_TRANSITIONS"},
    },
)


def test_comparison_reports_naive_causal_claims_next_to_forgesync_concurrent_evidence(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    payload = tmp_path / "payload"
    payload.write_bytes(PAYLOAD)
    save_projection_documents(DOCUMENTS, tmp_path / "forgesync")

    exit_code = main(
        [
            "compare-track-a",
            "--raw-payload",
            str(payload),
            "--forgesync-documents",
            str(tmp_path / "forgesync"),
        ]
    )

    report = json.loads(capsys.readouterr().out)
    assert exit_code == 0
    assert report["naiveEstimates"][-1] == {
        "ruleId": "NA-CAUSE-1",
        "value": 1,
        "hiddenAssumptions": ["CONCURRENT_CONDITION_IS_CAUSE"],
    }
    assert report["forgesync"]["downtimeReasonCounts"] == {"CONCURRENT_EVIDENCE": 1}
    assert report["forgesync"]["compositeOee"] == {
        "status": "UNAVAILABLE",
        "percent": None,
        "reason": "QUALITY_COMPONENT_UNAVAILABLE",
    }
    assert report["forgesync"]["productionResultStatus"] == "NOT_OBSERVED"
    production_context = (tmp_path / "forgesync" / "production-context.json").read_bytes()
    assert report["forgesyncDocumentsSha256"]["production-context.json"] == (
        hashlib.sha256(production_context).hexdigest()
    )
    assert len(report["forgesyncDocumentsSha256"]) == 5
