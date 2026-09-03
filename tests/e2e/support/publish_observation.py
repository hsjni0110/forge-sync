"""Publish pinned canonical Observations for browser checks and the guided local demo."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

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
GUIDED_DEMO_DATA_ITEM_IDS = frozenset(("Mazak01-path_13", "Mazak01-C_5"))
GUIDED_DEMO_SOURCE_START = "2016-10-05T09:18:27.292Z"
GUIDED_DEMO_SOURCE_END = "2016-10-05T10:18:22.999Z"
GUIDED_DEMO_EXPECTED_OBSERVATION_COUNT = 337
GUIDED_DEMO_INTERVAL_SECONDS = 1.5


def _load_object(path: Path) -> dict[str, Any]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return document


def _read_pinned_observations(scenario: dict[str, Any], step_name: str) -> list[dict[str, Any]]:
    canonical_path = REPOSITORY_ROOT / str(scenario["canonicalRun"])
    canonical_bytes = canonical_path.read_bytes()
    actual_sha256 = hashlib.sha256(canonical_bytes).hexdigest()
    if actual_sha256 != scenario["canonicalSha256"]:
        raise ValueError("Canonical Observation checksum does not match the E2E scenario")

    lines = canonical_bytes.splitlines()
    observations: list[dict[str, Any]] = []
    for descriptor in scenario["steps"][step_name]["observations"]:
        line_number = int(descriptor["line"])
        observation = json.loads(lines[line_number - 1])
        transformation = observation["provenance"]["transformation"]
        if transformation["sourceDataItemId"] != descriptor["sourceDataItemId"]:
            raise ValueError("Pinned source DataItem does not match the E2E scenario")
        if observation["payload"]["value"] != descriptor["expectedValue"]:
            raise ValueError("Pinned source value does not match the E2E scenario")

        observation["replay"] = {
            "replaySessionId": scenario["replaySessionId"],
            "replaySequence": descriptor["replaySequence"],
            "replayPublishedAt": descriptor["replayPublishedAt"],
        }
        observations.append(observation)
    return observations


def _read_guided_demo_observations(scenario: dict[str, Any]) -> list[dict[str, Any]]:
    canonical_path = REPOSITORY_ROOT / str(scenario["canonicalRun"])
    canonical_bytes = canonical_path.read_bytes()
    actual_sha256 = hashlib.sha256(canonical_bytes).hexdigest()
    if actual_sha256 != scenario["canonicalSha256"]:
        raise ValueError("Canonical Observation checksum does not match the guided demo")

    observations: list[dict[str, Any]] = []
    for encoded_line in canonical_bytes.splitlines():
        observation = json.loads(encoded_line)
        source_data_item_id = observation["provenance"]["transformation"]["sourceDataItemId"]
        source_observed_at = observation["source"]["sourceObservedAt"]
        if (
            source_data_item_id in GUIDED_DEMO_DATA_ITEM_IDS
            and GUIDED_DEMO_SOURCE_START <= source_observed_at <= GUIDED_DEMO_SOURCE_END
        ):
            observations.append(observation)

    if len(observations) != GUIDED_DEMO_EXPECTED_OBSERVATION_COUNT:
        raise ValueError("Guided demo selection no longer matches its pinned source evidence")
    return observations


def _with_replay_identity(
    observation: dict[str, Any], replay_session_id: str, replay_sequence: int
) -> dict[str, Any]:
    observation["replay"] = {
        "replaySessionId": replay_session_id,
        "replaySequence": replay_sequence,
        "replayPublishedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
    }
    return observation


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "step",
        choices=(
            "initial",
            "live-update",
            "stopped",
            "reactivated",
            "offline-update",
            "guided-demo",
        ),
    )
    parser.add_argument(
        "--interval-seconds",
        default=GUIDED_DEMO_INTERVAL_SECONDS,
        type=float,
        help="guided-demo wall-clock interval; it does not represent source timing",
    )
    arguments = parser.parse_args()
    if arguments.interval_seconds <= 0:
        parser.error("--interval-seconds must be positive")
    scenario = _load_object(SCENARIO_PATH)
    is_guided_demo = arguments.step == "guided-demo"
    observations = (
        _read_guided_demo_observations(scenario)
        if is_guided_demo
        else _read_pinned_observations(scenario, arguments.step)
    )
    transport = PahoMqttV5Transport(
        PahoMqttConfig(
            host=os.environ.get("FORGESYNC_MQTT_HOST", "127.0.0.1"),
            port=int(os.environ.get("FORGESYNC_MQTT_PORT", "18884")),
            client_id=f"forgesync-machine-detail-e2e-{arguments.step}",
        )
    )
    sleeper = SystemSleeper()
    publisher = MqttReplayPublisher(
        transport,
        MqttObservationContract(ObservationSchemaValidator.from_path(SCHEMA_PATH)),
        sleeper,
    )
    try:
        guided_demo_session_id = str(uuid4())
        for index, observation in enumerate(observations, start=1):
            if is_guided_demo:
                if index > 1:
                    sleeper.sleep(arguments.interval_seconds)
                observation = _with_replay_identity(observation, guided_demo_session_id, index)
            publisher.publish(json.dumps(observation, separators=(",", ":")).encode())
    finally:
        transport.close()


if __name__ == "__main__":
    main()
