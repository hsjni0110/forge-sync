"""Explicit mapping from canonical observation values to the public JSON contract."""

from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from ...domain.observation import (
    ConditionPayload,
    EventPayload,
    ObservationEnvelope,
    ReplayIdentity,
    SamplePayload,
)

SCHEMA_VERSION = "2.1.0"


def serialize_observation(observation: ObservationEnvelope) -> bytes:
    return json.dumps(
        observation_to_dict(observation), separators=(",", ":"), sort_keys=True
    ).encode("utf-8")


def observation_to_dict(observation: ObservationEnvelope) -> dict[str, Any]:
    document: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "eventId": str(observation.event_id),
        "sourceEventKey": observation.source_event_key,
        "machineId": observation.machine_id,
        "subject": {"componentId": observation.subject.component_id},
        "observationKind": observation.observation_kind.value,
        "source": _source_to_dict(observation),
        "provenance": {
            "source": {
                "kind": "REAL",
                "provider": "NIST",
                "sourceSetId": observation.provenance.source.source_set_id,
                "artifactId": observation.provenance.source.artifact_id,
            },
            "transformation": {
                "rawRecordId": observation.provenance.transformation.raw_record_id,
                "mappingVersion": observation.provenance.transformation.mapping_version,
                "sourceDataItemId": (observation.provenance.transformation.source_data_item_id),
            },
        },
        "payload": _payload_to_dict(observation.payload),
    }
    if observation.replay is not None:
        document["replay"] = _replay_to_dict(observation.replay)
    return document


def _source_to_dict(observation: ObservationEnvelope) -> dict[str, Any]:
    source: dict[str, Any] = {
        "sourceObservedAt": _format_time(observation.source.source_observed_at)
    }
    if observation.source.agent_instance_id is not None:
        source["agentInstanceId"] = observation.source.agent_instance_id
    if observation.source.source_sequence is not None:
        source["sourceSequence"] = observation.source.source_sequence
    return source


def _replay_to_dict(replay: ReplayIdentity) -> dict[str, Any]:
    return {
        "replaySessionId": str(replay.replay_session_id),
        "replaySequence": replay.replay_sequence,
        "replayPublishedAt": _format_time(replay.replay_published_at),
    }


def _payload_to_dict(payload: SamplePayload | EventPayload | ConditionPayload) -> dict[str, Any]:
    if isinstance(payload, SamplePayload):
        sample: dict[str, Any] = {
            "metric": payload.metric.value,
            "availability": payload.availability.value,
        }
        if payload.value is not None and payload.unit is not None:
            sample["value"] = payload.value
            sample["unit"] = payload.unit.value
        return sample
    if isinstance(payload, EventPayload):
        event: dict[str, Any] = {
            "eventType": payload.event_type.value,
            "availability": payload.availability.value,
        }
        if payload.value is not None:
            event["value"] = payload.value
        return event
    condition: dict[str, Any] = {
        "conditionType": payload.condition_type,
        "level": payload.level.value,
    }
    optional_fields = {
        "nativeCode": payload.native_code,
        "nativeSeverity": payload.native_severity,
        "qualifier": payload.qualifier,
        "message": payload.message,
    }
    condition.update({key: value for key, value in optional_fields.items() if value is not None})
    return condition


def _format_time(value: datetime) -> str:
    rendered = value.isoformat()
    rendered = rendered[:-6] + "Z" if rendered.endswith("+00:00") else rendered
    offset_index = len(rendered) - 1 if rendered.endswith("Z") else _offset_index(rendered)
    timestamp, offset = rendered[:offset_index], rendered[offset_index:]
    if "." in timestamp:
        timestamp = timestamp.rstrip("0").rstrip(".")
    return timestamp + offset


def _offset_index(rendered: str) -> int:
    positive = rendered.find("+", 10)
    negative = rendered.find("-", 10)
    indexes = tuple(index for index in (positive, negative) if index >= 0)
    return min(indexes) if indexes else len(rendered)
