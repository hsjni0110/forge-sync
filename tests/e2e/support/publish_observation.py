"""Publish one pinned canonical Observation for the browser E2E journey."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any

from forgesync_edge.replay.adapter.outbound.mqtt import (
    MqttObservationContract,
    MqttReplayPublisher,
    ObservationSchemaValidator,
    PahoMqttConfig,
    PahoMqttV5Transport,
    SystemSleeper,
)

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
SCENARIO_PATH = REPOSITORY_ROOT / "tests/e2e/fixtures/machine-detail-replay.json"
SCHEMA_PATH = REPOSITORY_ROOT / "contracts/observation-envelope/v2/observation-envelope.schema.json"


def _load_object(path: Path) -> dict[str, Any]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return document


def _read_pinned_observation(scenario: dict[str, Any], step_name: str) -> dict[str, Any]:
    canonical_path = REPOSITORY_ROOT / str(scenario["canonicalRun"])
    canonical_bytes = canonical_path.read_bytes()
    actual_sha256 = hashlib.sha256(canonical_bytes).hexdigest()
    if actual_sha256 != scenario["canonicalSha256"]:
        raise ValueError("Canonical Observation checksum does not match the E2E scenario")

    step = scenario["steps"][step_name]
    line_number = int(step["line"])
    lines = canonical_bytes.splitlines()
    observation = json.loads(lines[line_number - 1])
    transformation = observation["provenance"]["transformation"]
    if transformation["sourceDataItemId"] != step["sourceDataItemId"]:
        raise ValueError("Pinned source DataItem does not match the E2E scenario")
    if observation["payload"]["value"] != step["expectedValue"]:
        raise ValueError("Pinned source value does not match the E2E scenario")

    observation["replay"] = {
        "replaySessionId": scenario["replaySessionId"],
        "replaySequence": step["replaySequence"],
        "replayPublishedAt": step["replayPublishedAt"],
    }
    return observation


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("step", choices=("initial", "live-update", "offline-update"))
    arguments = parser.parse_args()
    scenario = _load_object(SCENARIO_PATH)
    observation = _read_pinned_observation(scenario, arguments.step)
    transport = PahoMqttV5Transport(
        PahoMqttConfig(
            host=os.environ.get("FORGESYNC_MQTT_HOST", "127.0.0.1"),
            port=int(os.environ.get("FORGESYNC_MQTT_PORT", "18884")),
            client_id=f"forgesync-machine-detail-e2e-{arguments.step}",
        )
    )
    publisher = MqttReplayPublisher(
        transport,
        MqttObservationContract(ObservationSchemaValidator.from_path(SCHEMA_PATH)),
        SystemSleeper(),
    )
    try:
        publisher.publish(json.dumps(observation, separators=(",", ":")).encode())
    finally:
        transport.close()


if __name__ == "__main__":
    main()
