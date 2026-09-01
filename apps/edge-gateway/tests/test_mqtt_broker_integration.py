from __future__ import annotations

import os
import threading
from pathlib import Path
from typing import Any, cast

import paho.mqtt.client as mqtt
import pytest
from forgesync_edge.replay.adapter.outbound.mqtt import (
    MqttObservationContract,
    MqttReplayPublisher,
    ObservationSchemaValidator,
    PahoMqttConfig,
    PahoMqttV5Transport,
    SystemSleeper,
)
from paho.mqtt.enums import CallbackAPIVersion
from paho.mqtt.properties import Properties
from paho.mqtt.reasoncodes import ReasonCode

REPOSITORY_ROOT = Path(__file__).parents[3]
VALID_REPLAY = (
    REPOSITORY_ROOT / "tests/fixtures/canonical/v2/valid/event-execution.json"
).read_bytes()
SCHEMA_PATH = (
    REPOSITORY_ROOT / "contracts/observation-envelope" / "v2/observation-envelope.schema.json"
)

pytestmark = [
    pytest.mark.mqtt_integration,
    pytest.mark.skipif(
        os.environ.get("FORGESYNC_MQTT_INTEGRATION") != "1",
        reason="repository-local Mosquitto is not enabled",
    ),
]


def test_edge_publisher_delivers_exact_qos1_mqtt5_contract() -> None:
    host = os.environ.get("FORGESYNC_MQTT_HOST", "127.0.0.1")
    port = int(os.environ.get("FORGESYNC_MQTT_PORT", "18883"))
    received = threading.Event()
    messages: list[mqtt.MQTTMessage] = []
    connected = threading.Event()
    subscriber = mqtt.Client(
        callback_api_version=CallbackAPIVersion.VERSION2,
        client_id="forgesync-mqtt-contract-probe",
        protocol=mqtt.MQTTv5,
    )

    def on_connect(
        client: mqtt.Client,
        userdata: Any,
        flags: mqtt.ConnectFlags,
        reason_code: ReasonCode,
        properties: Properties | None,
    ) -> None:
        del userdata, flags, properties
        if not reason_code.is_failure:
            client.subscribe("forgesync/observations/+", qos=1)
            connected.set()

    def on_message(client: mqtt.Client, userdata: Any, message: mqtt.MQTTMessage) -> None:
        del client, userdata
        messages.append(message)
        received.set()

    subscriber.on_connect = on_connect
    subscriber.on_message = on_message
    subscriber.connect(host, port)
    subscriber.loop_start()
    transport: PahoMqttV5Transport | None = None
    try:
        assert connected.wait(5), "subscriber did not connect"
        transport = PahoMqttV5Transport(PahoMqttConfig(host, port, "forgesync-edge-contract-test"))
        publisher = MqttReplayPublisher(
            transport=transport,
            observation_contract=MqttObservationContract(
                ObservationSchemaValidator.from_path(SCHEMA_PATH)
            ),
            sleeper=SystemSleeper(),
        )

        publisher.publish(VALID_REPLAY)

        assert received.wait(5), "broker did not deliver observation"
        message = messages[0]
        assert message.topic == "forgesync/observations/Mazak01"
        assert message.payload == VALID_REPLAY
        assert message.qos == 1
        assert message.retain is False
        message_properties = cast(Any, message.properties)
        assert message_properties.ContentType == "application/vnd.forgesync.observation+json"
        assert message_properties.PayloadFormatIndicator == 1
        properties = dict(message_properties.UserProperty)
        assert properties["schema-version"] == "2.0.0"
        assert properties["message-key"].startswith("00d64db8-967e-41ba-9d09-fdd087710aac:")
    finally:
        if transport is not None:
            transport.close()
        subscriber.disconnect()
        subscriber.loop_stop()
