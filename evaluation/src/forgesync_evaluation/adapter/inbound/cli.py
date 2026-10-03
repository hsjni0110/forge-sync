"""Command line entry point for the naive baseline comparison."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections.abc import Sequence
from pathlib import Path

from forgesync_evaluation.adapter.outbound.projection_document_files import (
    load_projection_documents,
)
from forgesync_evaluation.adapter.outbound.projection_document_reader import read_disclosure
from forgesync_evaluation.adapter.outbound.shdr_readings import read_shdr_readings
from forgesync_evaluation.application.naive_track_a import estimate_naive_track_a
from forgesync_evaluation.application.track_a_comparison import compare_track_a
from forgesync_evaluation.domain.forgesync_disclosure import DisclosedValue, ForgeSyncDisclosure
from forgesync_evaluation.domain.naive_estimate import NaiveEstimate


def main(arguments: Sequence[str] | None = None) -> int:
    namespace = _parser().parse_args(arguments)
    if namespace.command == "compare-track-a":
        report = _compare_track_a(namespace.raw_payload, namespace.forgesync_documents)
    else:
        report = _naive_track_a(namespace.raw_payload)
    print(json.dumps(report, indent=2))
    return 0


def _naive_track_a(payload_path: Path) -> dict[str, object]:
    estimates = estimate_naive_track_a(read_shdr_readings(payload_path))
    return {
        "rawPayloadSha256": _sha256(payload_path),
        "estimates": [_estimate_document(estimate) for estimate in estimates],
    }


def _compare_track_a(payload_path: Path, documents_directory: Path) -> dict[str, object]:
    disclosure = read_disclosure(load_projection_documents(documents_directory))
    comparison = compare_track_a(read_shdr_readings(payload_path), disclosure)
    return {
        "rawPayloadSha256": _sha256(payload_path),
        "naiveEstimates": [_estimate_document(item) for item in comparison.naive_estimates],
        "forgesync": _disclosure_document(comparison.forgesync),
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forgesync-evaluation", description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    track_a = commands.add_parser("naive-track-a", help="Estimate Track A naive dashboard values")
    track_a.add_argument("--raw-payload", type=Path, required=True)
    comparison = commands.add_parser(
        "compare-track-a", help="Compare naive estimates with collected ForgeSync documents"
    )
    comparison.add_argument("--raw-payload", type=Path, required=True)
    comparison.add_argument("--forgesync-documents", type=Path, required=True)
    return parser


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _estimate_document(estimate: NaiveEstimate) -> dict[str, object]:
    return {
        "ruleId": estimate.rule_id,
        "value": estimate.value,
        "hiddenAssumptions": list(estimate.hidden_assumptions),
    }


def _disclosure_document(disclosure: ForgeSyncDisclosure) -> dict[str, object]:
    return {
        "productionResultStatus": disclosure.production_result_status,
        "partCount": _value_document(disclosure.part_count),
        "machiningRunCount": disclosure.machining_run_count,
        "completedMachiningRunCount": disclosure.completed_machining_run_count,
        "activeState": _value_document(disclosure.active_state),
        "unknownStatePercent": disclosure.unknown_state_percent,
        "counterAutomatic": _value_document(disclosure.counter_automatic),
        "compositeOee": _value_document(disclosure.composite_oee),
        "performance": _value_document(disclosure.performance),
        "downtimeCount": len(disclosure.downtimes),
        "downtimeReasonCounts": dict(disclosure.downtime_reason_counts),
    }


def _value_document(value: DisclosedValue) -> dict[str, object]:
    return {"status": value.status, "percent": value.percent, "reason": value.reason}
