from __future__ import annotations

import json
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, cast
from uuid import UUID

import paho.mqtt.client as mqtt
import pytest
from forgesync_edge.replay.adapter.outbound.json_envelope import JsonReplayEnvelopeEncoder
from forgesync_edge.replay.adapter.outbound.mqtt import (
    MAX_OBSERVATION_BYTES,
    MqttObservationContract,
    MqttObservationContractError,
    MqttPublishRequest,
    MqttPublishUnavailable,
    MqttReplayPublisher,
    MqttTransport,
    MqttTransportUnavailable,
    ObservationSchemaValidator,
    PahoMqttConfig,
    PahoMqttV5Transport,
)
from forgesync_edge.replay.application.service import PublishDueObservation
from forgesync_edge.replay.domain import ReplayObservation, ReplaySession, ReplaySpeed, ReplayStatus

REPOSITORY_ROOT = Path(__file__).parents[3]
SCHEMA_PATH = (
    REPOSITORY_ROOT
    / "contracts"
    / "observation-envelope"
    / "v2"
    / "observation-envelope.schema.json"
)
VALID_REPLAY = (
    REPOSITORY_ROOT / "tests/fixtures/canonical/v2/valid/event-execution.json"
).read_bytes()


@dataclass
class RecordingTransport:
    failures_remaining: int = 0
    requests: list[MqttPublishRequest] = field(default_factory=list)

    def publish_and_await_puback(self, request: MqttPublishRequest, timeout_seconds: float) -> None:
        del timeout_seconds
        self.requests.append(request)
        if self.failures_remaining:
            self.failures_remaining -= 1
            raise MqttTransportUnavailable("broker unavailable")


@dataclass
class UnexpectedFailureTransport:
    attempts: int = 0

    def publish_and_await_puback(self, request: MqttPublishRequest, timeout_seconds: float) -> None:
        del request, timeout_seconds
        self.attempts += 1
        raise RuntimeError("programming defect")


@dataclass
class PublishedMessage:
    wait_timeouts: list[float] = field(default_factory=list)

    def wait_for_publish(self, timeout: float) -> None:
        self.wait_timeouts.append(timeout)

    def is_published(self) -> bool:
        return True


@dataclass
class RecordingPahoClient:
    is_connected_value: bool = False
    connect_calls: int = 0
    loop_start_calls: int = 0
    loop_stop_calls: int = 0
    disconnect_calls: int = 0
    published_messages: list[PublishedMessage] = field(default_factory=list)
    connect_timeout: float = 0

    def is_connected(self) -> bool:
        return self.is_connected_value

    def connect(self, *args: object, **kwargs: object) -> None:
        del args, kwargs
        self.connect_calls += 1
        self.is_connected_value = True

    def loop_start(self) -> None:
        self.loop_start_calls += 1

    def loop_stop(self) -> None:
        self.loop_stop_calls += 1

    def disconnect(self) -> None:
        self.disconnect_calls += 1
        self.is_connected_value = False

    def publish(self, *args: object, **kwargs: object) -> Any:
        del args, kwargs
        message = PublishedMessage()
        self.published_messages.append(message)
        return message


@dataclass
class RecordingSleeper:
    delays: list[float] = field(default_factory=list)

    def sleep(self, delay_seconds: float) -> None:
        self.delays.append(delay_seconds)


@dataclass(frozen=True)
class FixedClock:
    current: datetime

    def now(self) -> datetime:
        return self.current


def test_publishes_observation_v2_with_exact_mqtt5_contract() -> None:
    transport = RecordingTransport()
    publisher = _publisher(transport)

    publisher.publish(VALID_REPLAY)

    request = transport.requests[0]
    assert request.topic == "forgesync/observations/Mazak01"
    assert request.payload == VALID_REPLAY
    assert request.qos == 1
    assert request.is_retained is False
    assert request.content_type == "application/vnd.forgesync.observation+json"
    assert request.schema_version == "2.0.0"
    assert request.message_key.startswith("00d64db8-967e-41ba-9d09-fdd087710aac:")


def test_retries_transport_failure_twice_then_reports_stable_error() -> None:
    transport = RecordingTransport(failures_remaining=3)
    sleeper = RecordingSleeper()
    publisher = _publisher(transport, sleeper)

    with pytest.raises(MqttPublishUnavailable, match="PUBACK"):
        publisher.publish(VALID_REPLAY)

    assert len(transport.requests) == 3
    assert sleeper.delays == [0.1, 0.5]


def test_does_not_retry_unexpected_programming_failure() -> None:
    transport = UnexpectedFailureTransport()
    sleeper = RecordingSleeper()

    with pytest.raises(RuntimeError, match="programming defect"):
        _publisher(transport, sleeper).publish(VALID_REPLAY)

    assert transport.attempts == 1
    assert sleeper.delays == []


def test_paho_transport_reuses_connection_until_closed() -> None:
    client = RecordingPahoClient()
    transport = PahoMqttV5Transport(
        PahoMqttConfig("localhost", 1883, "edge-test"), cast(mqtt.Client, client)
    )
    request = _publisher_request()

    transport.publish_and_await_puback(request, 2.0)
    transport.publish_and_await_puback(request, 2.0)
    transport.close()

    assert client.connect_calls == 1
    assert client.loop_start_calls == 1
    assert len(client.published_messages) == 2
    assert client.disconnect_calls == 1
    assert client.loop_stop_calls == 1


def test_exhausted_puback_retry_keeps_replay_position_for_retry() -> None:
    document = json.loads(VALID_REPLAY)
    document.pop("replay")
    observed_at = datetime.fromisoformat(
        document["source"]["sourceObservedAt"].replace("Z", "+00:00")
    )
    observation = ReplayObservation(
        document["sourceEventKey"],
        observed_at,
        json.dumps(document, separators=(",", ":"), sort_keys=True).encode(),
    )
    now = datetime(2026, 9, 1, tzinfo=UTC)
    session = ReplaySession(
        UUID("61c7fe98-d1cd-4a2c-9ea8-24f72cc714db"),
        (observation,),
        ReplaySpeed.X1,
        now,
    )
    transport = RecordingTransport(failures_remaining=3)
    use_case = PublishDueObservation(
        clock=FixedClock(now),
        encoder=JsonReplayEnvelopeEncoder(),
        publisher=_publisher(transport),
    )

    with pytest.raises(MqttPublishUnavailable):
        use_case.publish_next(session)

    assert session.status is ReplayStatus.RUNNING
    assert session.replay_sequence.value == 0
    assert session.current_observation.source_event_key == document["sourceEventKey"]


@pytest.mark.parametrize(
    ("mutate", "message"),
    [
        (lambda item: item.pop("replay"), "replay identity"),
        (lambda item: item.update(schemaVersion="9.0.0"), "schema"),
        (lambda item: item.update(machineId="Mazak/01"), "machineId"),
    ],
)
def test_rejects_invalid_transport_payload_before_broker(
    mutate: Callable[[dict[str, object]], object], message: str
) -> None:
    document = json.loads(VALID_REPLAY)
    mutate(document)
    transport = RecordingTransport()

    with pytest.raises(MqttObservationContractError, match=message):
        _publisher(transport).publish(json.dumps(document).encode())

    assert transport.requests == []


def test_rejects_oversized_payload_before_parsing_or_broker() -> None:
    transport = RecordingTransport()

    with pytest.raises(MqttObservationContractError, match="65,536"):
        _publisher(transport).publish(b"x" * (MAX_OBSERVATION_BYTES + 1))

    assert transport.requests == []


def _publisher(
    transport: MqttTransport, sleeper: RecordingSleeper | None = None
) -> MqttReplayPublisher:
    return MqttReplayPublisher(
        transport=transport,
        observation_contract=MqttObservationContract(
            ObservationSchemaValidator.from_path(SCHEMA_PATH)
        ),
        sleeper=sleeper or RecordingSleeper(),
    )


def _publisher_request() -> MqttPublishRequest:
    transport = RecordingTransport()
    _publisher(transport).publish(VALID_REPLAY)
    return transport.requests[0]
