"""Deterministic replay planning command."""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import UUID

from ...application.plan import ReplayPlan, build_replay_plan
from ...domain import ReplaySpeed
from ..outbound import FilesystemReplaySourceReader


def main(arguments: Sequence[str] | None = None) -> int:
    parser = _parser()
    namespace = parser.parse_args(arguments)
    try:
        result = _plan(namespace)
    except (OSError, ValueError) as error:
        print(
            json.dumps({"error": "REPLAY_PLAN_ERROR", "message": str(error)}, sort_keys=True),
            file=sys.stderr,
        )
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forgesync-replay")
    subparsers = parser.add_subparsers(dest="command", required=True)
    plan = subparsers.add_parser("plan")
    plan.add_argument("--canonical-run", required=True, type=Path)
    plan.add_argument("--session-id", required=True, type=UUID)
    plan.add_argument("--started-at", required=True, type=_aware_time)
    plan.add_argument("--speed", required=True, type=int, choices=(1, 10, 100))
    plan.add_argument("--output", required=True, type=Path)
    return parser


def _plan(namespace: argparse.Namespace) -> dict[str, object]:
    observations = FilesystemReplaySourceReader().read(namespace.canonical_run)
    plan = build_replay_plan(
        observations,
        namespace.session_id,
        namespace.started_at,
        ReplaySpeed.from_multiplier(namespace.speed),
    )
    manifest = _read_manifest(namespace.canonical_run / "manifest.json")
    report = _report(plan, manifest)
    json_path, markdown_path = _write_report(report, namespace.output)
    return {
        "command": "plan",
        "observationCount": plan.observation_count,
        "sequenceHash": plan.sequence_hash,
        "reportJson": str(json_path),
        "reportMarkdown": str(markdown_path),
    }


def _report(plan: ReplayPlan, manifest: dict[str, Any]) -> dict[str, object]:
    processing_run_id = manifest.get("processingRunId")
    outputs = manifest.get("outputs")
    observation_identity = outputs.get("observations.ndjson") if isinstance(outputs, dict) else None
    observations_sha256 = (
        observation_identity.get("sha256") if isinstance(observation_identity, dict) else None
    )
    if not isinstance(processing_run_id, str) or not isinstance(observations_sha256, str):
        raise ValueError("Canonical manifest lacks replay planning identity")
    return {
        "reportVersion": "1.0.0",
        "canonicalProcessingRunId": processing_run_id,
        "canonicalObservationsSha256": observations_sha256,
        "replaySessionId": str(plan.replay_session_id),
        "speedMultiplier": plan.speed.value,
        "observationCount": plan.observation_count,
        "sourceStartsAt": _format_time(plan.source_starts_at),
        "sourceEndsAt": _format_time(plan.source_ends_at),
        "replayStartsAt": _format_time(plan.replay_starts_at),
        "replayEndsAt": _format_time(plan.replay_ends_at),
        "sequenceHash": plan.sequence_hash,
        "sequenceHashInput": "canonical JSON lines of replaySequence and sourceEventKey",
    }


def _write_report(report: dict[str, object], output: Path) -> tuple[Path, Path]:
    output.mkdir(parents=True, exist_ok=True)
    json_path = output / "replay-plan.json"
    markdown_path = output / "replay-plan.md"
    json_content = (json.dumps(report, indent=2, sort_keys=True) + "\n").encode()
    markdown_content = _render_markdown(report).encode()
    _write_if_same_or_absent(json_path, json_content)
    _write_if_same_or_absent(markdown_path, markdown_content)
    return json_path, markdown_path


def _render_markdown(report: dict[str, object]) -> str:
    return f"""# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `{report["canonicalProcessingRunId"]}` |
| Canonical Observations SHA-256 | `{report["canonicalObservationsSha256"]}` |
| Replay Session | `{report["replaySessionId"]}` |
| Speed | `{report["speedMultiplier"]}x` |
| Observation Count | {report["observationCount"]} |
| Source Range | `{report["sourceStartsAt"]}` — `{report["sourceEndsAt"]}` |
| Planned Replay Range | `{report["replayStartsAt"]}` — `{report["replayEndsAt"]}` |
| Sequence Hash | `{report["sequenceHash"]}` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.
"""


def _read_manifest(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Canonical manifest must be an object")
    return value


def _aware_time(value: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise argparse.ArgumentTypeError("must be an ISO 8601 date-time") from error
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise argparse.ArgumentTypeError("must include a timezone")
    return parsed


def _format_time(value: datetime) -> str:
    rendered = value.isoformat()
    return rendered[:-6] + "Z" if rendered.endswith("+00:00") else rendered


def _write_if_same_or_absent(path: Path, content: bytes) -> None:
    if path.exists():
        if path.read_bytes() != content:
            raise ValueError(f"Replay report already exists with different content: {path}")
        return
    path.write_bytes(content)


if __name__ == "__main__":
    raise SystemExit(main())
