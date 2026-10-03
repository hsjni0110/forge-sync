"""Replay Edge API composed with a fault-injecting publisher, for evaluation runs only.

It reuses the product's public replay components unchanged; only the publisher is wrapped,
so the regular `forgesync-replay-api` composition keeps its delivery guarantees.
"""

from __future__ import annotations

import os
from pathlib import Path

import uvicorn
from fastapi import FastAPI
from forgesync_edge.replay.adapter.inbound.http import create_app
from forgesync_edge.replay.adapter.outbound import (
    FilesystemReplaySourceReader,
    JsonReplayEnvelopeEncoder,
)
from forgesync_edge.replay.adapter.outbound.mqtt import (
    MqttObservationContract,
    MqttReplayPublisher,
    ObservationSchemaValidator,
    PahoMqttConfig,
    PahoMqttV5Transport,
    SystemSleeper,
)
from forgesync_edge.replay.application.runtime import ReplayRuntime

from forgesync_evaluation.adapter.outbound.fault_injecting_publisher import (
    FaultInjectingPublisher,
)
from forgesync_evaluation.domain.fault_plan import fault_for, preregistered_scenario


def configured_app() -> FastAPI:
    source_path = Path(_required_environment("FORGESYNC_REPLAY_SOURCE_PATH"))
    source_set_id = _required_environment("FORGESYNC_REPLAY_SOURCE_SET_ID")
    repository_root = Path(_required_environment("FORGESYNC_REPOSITORY_ROOT"))
    scenario = preregistered_scenario(_required_environment("FORGESYNC_EVALUATION_SCENARIO"))
    envelope_count = _count_lines(source_path / "observations.ndjson")
    validator = ObservationSchemaValidator.from_path(
        repository_root / "contracts/observation-envelope/v2/observation-envelope.schema.json"
    )
    transport = PahoMqttV5Transport(
        PahoMqttConfig(
            host=os.getenv("FORGESYNC_MQTT_HOST", "127.0.0.1"),
            port=int(_required_environment("FORGESYNC_MQTT_PORT")),
            client_id=_required_environment("FORGESYNC_MQTT_CLIENT_ID"),
        )
    )
    publisher = FaultInjectingPublisher(
        MqttReplayPublisher(transport, MqttObservationContract(validator), SystemSleeper()),
        lambda index: fault_for(scenario, index, envelope_count),
    )
    return create_app(
        ReplayRuntime(
            {source_set_id: source_path},
            publisher,
            FilesystemReplaySourceReader(),
            JsonReplayEnvelopeEncoder(),
        )
    )


def _count_lines(path: Path) -> int:
    with path.open("rb") as lines:
        return sum(1 for _ in lines)


def _required_environment(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"{name} must be configured")
    return value


def main() -> None:
    uvicorn.run(
        configured_app(),
        host="127.0.0.1",
        port=int(_required_environment("FORGESYNC_REPLAY_API_PORT")),
    )
