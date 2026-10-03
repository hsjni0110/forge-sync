"""Command line entry point for naive baseline estimates."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections.abc import Sequence
from pathlib import Path

from forgesync_evaluation.adapter.outbound.shdr_readings import read_shdr_readings
from forgesync_evaluation.application.naive_track_a import estimate_naive_track_a
from forgesync_evaluation.domain.naive_estimate import NaiveEstimate


def main(arguments: Sequence[str] | None = None) -> int:
    namespace = _parser().parse_args(arguments)
    payload_path: Path = namespace.raw_payload
    estimates = estimate_naive_track_a(read_shdr_readings(payload_path))
    report = {
        "rawPayloadSha256": hashlib.sha256(payload_path.read_bytes()).hexdigest(),
        "estimates": [_estimate_document(estimate) for estimate in estimates],
    }
    print(json.dumps(report, indent=2))
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forgesync-evaluation", description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    track_a = commands.add_parser("naive-track-a", help="Estimate Track A naive dashboard values")
    track_a.add_argument("--raw-payload", type=Path, required=True)
    return parser


def _estimate_document(estimate: NaiveEstimate) -> dict[str, object]:
    return {
        "ruleId": estimate.rule_id,
        "value": estimate.value,
        "hiddenAssumptions": list(estimate.hidden_assumptions),
    }
