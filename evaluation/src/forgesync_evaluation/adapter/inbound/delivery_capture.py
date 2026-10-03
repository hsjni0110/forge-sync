"""Record every envelope the broker delivers, in arrival order, for the naive consumer."""

from __future__ import annotations

import argparse
import json
import signal
import threading
from collections.abc import Sequence
from pathlib import Path
from typing import Any

import paho.mqtt.client as mqtt
from paho.mqtt.enums import CallbackAPIVersion


def main(arguments: Sequence[str] | None = None) -> int:
    namespace = _parser().parse_args(arguments)
    output: Path = namespace.output
    ready_marker = output.with_name(output.name + ".ready")
    stopped = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as log:
        client = mqtt.Client(
            callback_api_version=CallbackAPIVersion.VERSION2, client_id=namespace.client_id
        )
        client.on_connect = lambda client, *_: client.subscribe(namespace.topic, qos=1)
        # The replay must not start before the subscription exists, or early envelopes are lost.
        client.on_subscribe = lambda *_: ready_marker.touch()
        client.on_message = lambda _client, _userdata, message: _record(log, message.payload)
        client.connect(namespace.host, namespace.port)
        client.loop_start()
        stopped.wait()
        client.loop_stop()
        client.disconnect()
    return 0


def _record(log: Any, payload: bytes) -> None:
    # One compact JSON document per line keeps arrival order readable as NDJSON.
    log.write(json.dumps(json.loads(payload), separators=(",", ":")) + "\n")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forgesync-evaluation-capture", description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--topic", required=True)
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser
