"""MQTT 5 QoS1 adapter for replayed Observation v2 envelopes."""

from __future__ import annotations

import json
import re
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

import paho.mqtt.client as mqtt
from jsonschema import Draft202012Validator, FormatChecker  # type: ignore[import-untyped]
from paho.mqtt.enums import CallbackAPIVersion
from paho.mqtt.packettypes import PacketTypes
from paho.mqtt.properties import Properties
from paho.mqtt.reasoncodes import ReasonCode

MAX_OBSERVATION_BYTES = 65_536
OBSERVATION_CONTENT_TYPE = "application/vnd.forgesync.observation+json"
SCHEMA_VERSION = "2.0.0"
MACHINE_TOPIC_SEGMENT = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


class MqttObservationContractError(ValueError):
    """The envelope cannot be represented by the MQTT delivery contract."""


class MqttTransportUnavailable(RuntimeError):
    """A technical MQTT operation did not complete."""


class MqttPublishUnavailable(RuntimeError):
    """The broker did not confirm a bounded QoS1 publication."""


@dataclass(frozen=True, slots=True)
class MqttPublishRequest:
    topic: str
    payload: bytes
    qos: int
    is_retained: bool
    content_type: str
    schema_version: str
    message_key: str


@dataclass(frozen=True, slots=True)
class ReplayRoutingIdentity:
    machine_id: str
    replay_session_id: str
    source_event_key: str


class MqttTransport(Protocol):
    def publish_and_await_puback(
        self, request: MqttPublishRequest, timeout_seconds: float
    ) -> None: ...


class Sleeper(Protocol):
    def sleep(self, delay_seconds: float) -> None: ...


class SystemSleeper:
    def sleep(self, delay_seconds: float) -> None:
        time.sleep(delay_seconds)


class ObservationSchemaValidator:
    def __init__(self, schema: dict[str, Any]) -> None:
        self._validator = Draft202012Validator(schema, format_checker=FormatChecker())

    @classmethod
    def from_path(cls, schema_path: Path) -> ObservationSchemaValidator:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        if not isinstance(schema, dict):
            raise ValueError("Observation schema must be an object")
        return cls(schema)

    def validate(self, document: object) -> None:
        violations = sorted(
            f"{error.json_path}:{error.validator}"
            for error in self._validator.iter_errors(document)
        )
        if violations:
            raise MqttObservationContractError(
                "Observation schema validation failed: " + ", ".join(violations)
            )


class MqttObservationContract:
    def __init__(self, schema_validator: ObservationSchemaValidator) -> None:
        self._schema_validator = schema_validator

    def create_publish_request(self, envelope: bytes) -> MqttPublishRequest:
        document = self._decode_and_validate(envelope)
        routing = self._replay_routing(document)
        return MqttPublishRequest(
            topic=f"forgesync/observations/{routing.machine_id}",
            payload=envelope,
            qos=1,
            is_retained=False,
            content_type=OBSERVATION_CONTENT_TYPE,
            schema_version=SCHEMA_VERSION,
            message_key=f"{routing.replay_session_id}:{routing.source_event_key}",
        )

    def _decode_and_validate(self, envelope: bytes) -> dict[str, Any]:
        if len(envelope) > MAX_OBSERVATION_BYTES:
            raise MqttObservationContractError("MQTT payload exceeds 65,536 bytes")
        try:
            document = json.loads(envelope)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise MqttObservationContractError(
                "MQTT payload must be UTF-8 Observation JSON"
            ) from error
        self._schema_validator.validate(document)
        if not isinstance(document, dict):
            raise MqttObservationContractError("Observation must be an object")
        return document

    @staticmethod
    def _replay_routing(document: dict[str, Any]) -> ReplayRoutingIdentity:
        replay = document.get("replay")
        if not isinstance(replay, dict):
            raise MqttObservationContractError("MQTT Observation requires replay identity")
        machine_id = document.get("machineId")
        if not isinstance(machine_id, str) or not MACHINE_TOPIC_SEGMENT.fullmatch(machine_id):
            raise MqttObservationContractError("machineId is not a safe MQTT topic segment")
        replay_session_id = replay.get("replaySessionId")
        source_event_key = document.get("sourceEventKey")
        if not isinstance(replay_session_id, str) or not isinstance(source_event_key, str):
            raise MqttObservationContractError("MQTT Observation has invalid replay identity")
        return ReplayRoutingIdentity(machine_id, replay_session_id, source_event_key)


class MqttReplayPublisher:
    def __init__(
        self,
        transport: MqttTransport,
        observation_contract: MqttObservationContract,
        sleeper: Sleeper,
        *,
        publish_timeout_seconds: float = 5.0,
    ) -> None:
        if publish_timeout_seconds <= 0:
            raise ValueError("publish timeout must be positive")
        self._transport = transport
        self._observation_contract = observation_contract
        self._sleeper = sleeper
        self._publish_timeout_seconds = publish_timeout_seconds

    def publish(self, envelope: bytes) -> None:
        request = self._observation_contract.create_publish_request(envelope)
        retry_delays = (0.1, 0.5)
        for attempt in range(3):
            try:
                self._transport.publish_and_await_puback(request, self._publish_timeout_seconds)
                return
            except MqttTransportUnavailable as error:
                if attempt == 2:
                    raise MqttPublishUnavailable(
                        "MQTT broker did not confirm QoS1 PUBACK after 3 attempts"
                    ) from error
                self._sleeper.sleep(retry_delays[attempt])
        raise AssertionError("unreachable retry state")


@dataclass(frozen=True, slots=True)
class PahoMqttConfig:
    host: str
    port: int
    client_id: str
    username: str | None = None
    password: str | None = None
    keep_alive_seconds: int = 30

    def __post_init__(self) -> None:
        if not self.host or not self.client_id:
            raise ValueError("MQTT host and client_id must not be empty")
        if not 1 <= self.port <= 65_535:
            raise ValueError("MQTT port must be between 1 and 65535")
        if self.keep_alive_seconds <= 0:
            raise ValueError("MQTT keep alive must be positive")
        if self.password is not None and self.username is None:
            raise ValueError("MQTT password requires username")


class PahoMqttV5Transport:
    def __init__(self, config: PahoMqttConfig, client: mqtt.Client | None = None) -> None:
        self._config = config
        self._client = client or self._create_client(config)
        self._connected = threading.Event()
        self._is_network_loop_running = False
        self._lock = threading.Lock()
        self._client.on_connect = self._record_connection
        self._client.on_disconnect = self._record_disconnection

    def publish_and_await_puback(self, request: MqttPublishRequest, timeout_seconds: float) -> None:
        with self._lock:
            try:
                self._ensure_connected(timeout_seconds)
                publication = self._client.publish(
                    request.topic,
                    request.payload,
                    qos=request.qos,
                    retain=request.is_retained,
                    properties=_publish_properties(request),
                )
                publication.wait_for_publish(timeout=timeout_seconds)
                if not publication.is_published():
                    raise TimeoutError("MQTT PUBACK timed out")
            except (OSError, RuntimeError, TimeoutError) as error:
                self._reset_connection_after_failure()
                raise MqttTransportUnavailable("MQTT publish operation failed") from error

    def close(self) -> None:
        with self._lock:
            self._reset_connection()

    def _ensure_connected(self, timeout_seconds: float) -> None:
        if self._client.is_connected():
            return
        self._client.connect_timeout = timeout_seconds
        self._client.connect(
            self._config.host,
            self._config.port,
            keepalive=self._config.keep_alive_seconds,
            clean_start=mqtt.MQTT_CLEAN_START_FIRST_ONLY,
        )
        if not self._is_network_loop_running:
            self._client.loop_start()
            self._is_network_loop_running = True
        if not self._client.is_connected() and not self._connected.wait(timeout_seconds):
            raise TimeoutError("MQTT CONNACK timed out")
        if not self._client.is_connected():
            raise RuntimeError("MQTT broker rejected connection")

    def _reset_connection(self) -> None:
        self._connected.clear()
        if self._client.is_connected():
            self._client.disconnect()
        if self._is_network_loop_running:
            self._client.loop_stop()
            self._is_network_loop_running = False

    def _reset_connection_after_failure(self) -> None:
        try:
            self._reset_connection()
        except (OSError, RuntimeError):
            self._is_network_loop_running = False

    def _record_connection(
        self,
        client: mqtt.Client,
        userdata: Any,
        flags: mqtt.ConnectFlags,
        reason_code: ReasonCode,
        properties: Properties | None,
    ) -> None:
        del client, userdata, flags, properties
        if not reason_code.is_failure:
            self._connected.set()

    def _record_disconnection(
        self,
        client: mqtt.Client,
        userdata: Any,
        disconnect_flags: mqtt.DisconnectFlags,
        reason_code: ReasonCode,
        properties: Properties | None,
    ) -> None:
        del client, userdata, disconnect_flags, reason_code, properties
        self._connected.clear()

    @staticmethod
    def _create_client(config: PahoMqttConfig) -> mqtt.Client:
        client = mqtt.Client(
            callback_api_version=CallbackAPIVersion.VERSION2,
            client_id=config.client_id,
            protocol=mqtt.MQTTv5,
        )
        if config.username is not None:
            client.username_pw_set(config.username, config.password)
        return client


def _publish_properties(request: MqttPublishRequest) -> Properties:
    properties = Properties(PacketTypes.PUBLISH)  # type: ignore[no-untyped-call]
    properties.PayloadFormatIndicator = 1
    properties.ContentType = request.content_type
    properties.UserProperty = [
        ("schema-version", request.schema_version),
        ("message-key", request.message_key),
    ]
    return properties
