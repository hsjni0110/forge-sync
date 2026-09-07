"""Verified reader for immutable L2 Canonical Processing Runs."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime
from pathlib import Path
from typing import Any

from ...domain import ReplayObservation

# 2.1.0 only adds canonical vocabulary, so runs recorded under 2.0.0 stay replayable.
SUPPORTED_SCHEMA_VERSIONS = frozenset({"2.0.0", "2.1.0"})


class FilesystemReplaySourceReader:
    def read(self, canonical_run: Path) -> tuple[ReplayObservation, ...]:
        manifest = _read_object(canonical_run / "manifest.json", "manifest")
        observations_path = canonical_run / "observations.ndjson"
        content = observations_path.read_bytes()
        _verify_output_identity(manifest, content)
        observations = _read_observations(content)
        expected_count = manifest.get("observationCount")
        if expected_count != len(observations):
            raise ValueError("Canonical manifest observation count differs from its output")
        return observations


def _verify_output_identity(manifest: dict[str, Any], content: bytes) -> None:
    outputs = manifest.get("outputs")
    identity = outputs.get("observations.ndjson") if isinstance(outputs, dict) else None
    if not isinstance(identity, dict):
        raise ValueError("Canonical manifest lacks observations output identity")
    if identity.get("byteLength") != len(content):
        raise ValueError("Canonical observations byte length differs from manifest")
    digest = hashlib.sha256(content).hexdigest()
    if identity.get("sha256") != digest:
        raise ValueError("Canonical observations checksum differs from manifest")


def _read_observations(content: bytes) -> tuple[ReplayObservation, ...]:
    observations: list[ReplayObservation] = []
    source_event_keys: set[str] = set()
    for line_number, canonical_envelope in enumerate(content.splitlines(), start=1):
        if not canonical_envelope:
            raise ValueError(f"Canonical observations line {line_number} is empty")
        try:
            document = json.loads(canonical_envelope)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ValueError(f"Canonical observations line {line_number} is not JSON") from error
        observation = _adapt_observation(document, canonical_envelope, line_number)
        if observation.source_event_key in source_event_keys:
            raise ValueError(f"Duplicate sourceEventKey at line {line_number}")
        source_event_keys.add(observation.source_event_key)
        observations.append(observation)
    if not observations:
        raise ValueError("Canonical observations output is empty")
    return tuple(observations)


def _adapt_observation(
    document: object, canonical_envelope: bytes, line_number: int
) -> ReplayObservation:
    if not isinstance(document, dict):
        raise ValueError(f"Canonical observations line {line_number} must be an object")
    if document.get("schemaVersion") not in SUPPORTED_SCHEMA_VERSIONS:
        raise ValueError(f"Unsupported Observation schema at line {line_number}")
    if "replay" in document:
        raise ValueError(
            f"Canonical observation at line {line_number} already contains replay identity"
        )
    source_event_key = document.get("sourceEventKey")
    source = document.get("source")
    source_observed_at = source.get("sourceObservedAt") if isinstance(source, dict) else None
    if not isinstance(source_event_key, str) or not source_event_key:
        raise ValueError(f"Invalid sourceEventKey at line {line_number}")
    if not isinstance(source_observed_at, str):
        raise ValueError(f"Invalid sourceObservedAt at line {line_number}")
    observed_at = _parse_aware_time(source_observed_at, line_number)
    return ReplayObservation(source_event_key, observed_at, canonical_envelope)


def _parse_aware_time(value: str, line_number: int) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"Invalid sourceObservedAt at line {line_number}") from error
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError(f"sourceObservedAt lacks timezone at line {line_number}")
    return parsed


def _read_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Cannot read Canonical {label}: {path}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Canonical {label} must be an object")
    return value
