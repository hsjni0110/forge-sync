"""Canonical observation values independent of JSON and transport frameworks."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from uuid import UUID


class ObservationKind(StrEnum):
    SAMPLE = "SAMPLE"
    EVENT = "EVENT"
    CONDITION = "CONDITION"


class Availability(StrEnum):
    AVAILABLE = "AVAILABLE"
    UNAVAILABLE = "UNAVAILABLE"


class SampleMetric(StrEnum):
    SPINDLE_SPEED = "SPINDLE_SPEED"
    PATH_FEEDRATE = "PATH_FEEDRATE"
    TEMPERATURE = "TEMPERATURE"
    LOAD = "LOAD"
    POSITION = "POSITION"


class Unit(StrEnum):
    REVOLUTION_PER_MINUTE = "REVOLUTION/MINUTE"
    MILLIMETER_PER_SECOND = "MILLIMETER/SECOND"
    CELSIUS = "CELSIUS"
    PERCENT = "PERCENT"
    MILLIMETER = "MILLIMETER"


EXPECTED_UNITS = {
    SampleMetric.SPINDLE_SPEED: Unit.REVOLUTION_PER_MINUTE,
    SampleMetric.PATH_FEEDRATE: Unit.MILLIMETER_PER_SECOND,
    SampleMetric.TEMPERATURE: Unit.CELSIUS,
    SampleMetric.LOAD: Unit.PERCENT,
    SampleMetric.POSITION: Unit.MILLIMETER,
}


class EventType(StrEnum):
    EXECUTION = "EXECUTION"
    CONTROLLER_MODE = "CONTROLLER_MODE"
    TOOL_NUMBER = "TOOL_NUMBER"
    PROGRAM = "PROGRAM"
    PART_COUNT = "PART_COUNT"
    AVAILABILITY = "AVAILABILITY"
    POWER_STATE = "POWER_STATE"


class ConditionLevel(StrEnum):
    NORMAL = "NORMAL"
    WARNING = "WARNING"
    FAULT = "FAULT"
    UNAVAILABLE = "UNAVAILABLE"


@dataclass(frozen=True, slots=True)
class SourceIdentity:
    source_observed_at: datetime
    agent_instance_id: str | None = None
    source_sequence: int | None = None

    def __post_init__(self) -> None:
        _require_aware_time(self.source_observed_at, "source_observed_at")
        _require_non_empty_if_present(self.agent_instance_id, "agent_instance_id")
        _require_non_negative_if_present(self.source_sequence, "source_sequence")


@dataclass(frozen=True, slots=True)
class ReplayIdentity:
    replay_session_id: UUID
    replay_sequence: int
    replay_published_at: datetime

    def __post_init__(self) -> None:
        _require_non_negative_if_present(self.replay_sequence, "replay_sequence")
        _require_aware_time(self.replay_published_at, "replay_published_at")


@dataclass(frozen=True, slots=True)
class ObservationSubject:
    component_id: str

    def __post_init__(self) -> None:
        _require_non_empty(self.component_id, "component_id")


@dataclass(frozen=True, slots=True)
class ProvenanceSource:
    source_set_id: str
    artifact_id: str

    def __post_init__(self) -> None:
        _require_non_empty(self.source_set_id, "source_set_id")
        if not self.artifact_id.startswith("sha256:") or len(self.artifact_id) != 71:
            raise ValueError("artifact_id must be a sha256 content identity")


@dataclass(frozen=True, slots=True)
class TransformationProvenance:
    raw_record_id: str
    mapping_version: str
    source_data_item_id: str

    def __post_init__(self) -> None:
        _require_non_empty(self.raw_record_id, "raw_record_id")
        _require_non_empty(self.mapping_version, "mapping_version")
        _require_non_empty(self.source_data_item_id, "source_data_item_id")


@dataclass(frozen=True, slots=True)
class Provenance:
    source: ProvenanceSource
    transformation: TransformationProvenance


@dataclass(frozen=True, slots=True)
class SamplePayload:
    metric: SampleMetric
    availability: Availability
    value: float | None = None
    unit: Unit | None = None

    def __post_init__(self) -> None:
        if self.availability is Availability.UNAVAILABLE:
            if self.value is not None or self.unit is not None:
                raise ValueError("unavailable sample must not invent a value or unit")
            return
        if self.value is None or self.unit is None:
            raise ValueError("available sample requires value and unit")
        if self.unit is not EXPECTED_UNITS[self.metric]:
            raise ValueError(f"{self.metric.value} requires {EXPECTED_UNITS[self.metric].value}")


@dataclass(frozen=True, slots=True)
class EventPayload:
    event_type: EventType
    availability: Availability
    value: str | int | None = None

    def __post_init__(self) -> None:
        if self.availability is Availability.UNAVAILABLE:
            if self.value is not None:
                raise ValueError("unavailable event must not invent a value")
            return
        if self.value is None or isinstance(self.value, bool):
            raise ValueError("available event requires a string or integer value")
        numeric_event = self.event_type in {EventType.TOOL_NUMBER, EventType.PART_COUNT}
        if numeric_event and (not isinstance(self.value, int) or self.value < 0):
            raise ValueError(f"{self.event_type.value} requires a non-negative integer")
        if not numeric_event and (not isinstance(self.value, str) or not self.value):
            raise ValueError(f"{self.event_type.value} requires a non-empty string")


@dataclass(frozen=True, slots=True)
class ConditionPayload:
    condition_type: str
    level: ConditionLevel
    native_code: str | None = None
    native_severity: str | None = None
    qualifier: str | None = None
    message: str | None = None

    def __post_init__(self) -> None:
        _require_non_empty(self.condition_type, "condition_type")


ObservationPayload = SamplePayload | EventPayload | ConditionPayload


@dataclass(frozen=True, slots=True)
class ObservationEnvelope:
    event_id: UUID
    source_event_key: str
    machine_id: str
    subject: ObservationSubject
    source: SourceIdentity
    provenance: Provenance
    payload: ObservationPayload
    replay: ReplayIdentity | None = None

    def __post_init__(self) -> None:
        _require_non_empty(self.source_event_key, "source_event_key")
        _require_non_empty(self.machine_id, "machine_id")

    @property
    def observation_kind(self) -> ObservationKind:
        if isinstance(self.payload, SamplePayload):
            return ObservationKind.SAMPLE
        if isinstance(self.payload, EventPayload):
            return ObservationKind.EVENT
        return ObservationKind.CONDITION


def _require_aware_time(value: datetime, field_name: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must include a timezone")


def _require_non_empty(value: str, field_name: str) -> None:
    if not value:
        raise ValueError(f"{field_name} must not be empty")


def _require_non_empty_if_present(value: str | None, field_name: str) -> None:
    if value is not None:
        _require_non_empty(value, field_name)


def _require_non_negative_if_present(value: int | None, field_name: str) -> None:
    if value is not None and value < 0:
        raise ValueError(f"{field_name} must be non-negative")
