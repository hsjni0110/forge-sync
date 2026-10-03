"""Command line entry point for the naive baseline comparison."""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from collections.abc import Sequence
from pathlib import Path

from forgesync_evaluation.adapter.outbound.factory_api_projections import (
    FactoryApiProjectionClient,
)
from forgesync_evaluation.adapter.outbound.projection_document_files import (
    FILE_NAMES,
    load_projection_documents,
    save_projection_documents,
)
from forgesync_evaluation.adapter.outbound.projection_document_reader import read_disclosure
from forgesync_evaluation.adapter.outbound.shdr_readings import read_shdr_readings
from forgesync_evaluation.application.forgesync_track_a import collect_projection_documents
from forgesync_evaluation.application.naive_track_a import estimate_naive_track_a
from forgesync_evaluation.application.replay_completion import (
    PollingPolicy,
    replay_to_completion,
)
from forgesync_evaluation.application.track_a_comparison import compare_track_a
from forgesync_evaluation.domain.forgesync_disclosure import DisclosedValue, ForgeSyncDisclosure
from forgesync_evaluation.domain.naive_estimate import NaiveEstimate


def main(arguments: Sequence[str] | None = None) -> int:
    namespace = _parser().parse_args(arguments)
    if namespace.command == "collect-forgesync":
        report = _collect_forgesync(namespace)
    elif namespace.command == "compare-track-a":
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
        "forgesyncDocumentsSha256": {
            file_name: _sha256(documents_directory / file_name) for file_name in FILE_NAMES.values()
        },
    }


# A full 100x replay of the pinned day takes about ten minutes; allow three times that.
REPLAY_POLLING = PollingPolicy(interval_seconds=5.0, max_polls=360)


def _collect_forgesync(namespace: argparse.Namespace) -> dict[str, object]:
    client = FactoryApiProjectionClient(namespace.api_base_url, namespace.machine_id)
    replay = replay_to_completion(
        client, namespace.source_set_id, namespace.speed_multiplier, REPLAY_POLLING, time.sleep
    )
    documents = collect_projection_documents(
        client, replay.replay_session_id, replay.through_replay_sequence
    )
    save_projection_documents(documents, namespace.output_directory)
    return {
        "replaySessionId": replay.replay_session_id,
        "publishedThroughReplaySequence": replay.through_replay_sequence,
        "collectedThroughReplaySequence": documents.machining_runs["throughReplaySequence"],
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
    collection = commands.add_parser(
        "collect-forgesync", help="Replay the source once and collect ForgeSync projections"
    )
    collection.add_argument("--api-base-url", required=True)
    collection.add_argument("--machine-id", required=True)
    collection.add_argument("--source-set-id", required=True)
    collection.add_argument("--speed-multiplier", type=int, choices=(1, 10, 100), default=100)
    collection.add_argument("--output-directory", type=Path, required=True)
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
