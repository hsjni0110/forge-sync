"""Pure semantic mapping policy from verified source candidates to observations."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from uuid import UUID

from .observation import (
    EXPECTED_UNITS,
    Availability,
    ConditionLevel,
    ConditionPayload,
    EventPayload,
    EventType,
    ObservationEnvelope,
    ObservationSubject,
    Provenance,
    ProvenanceSource,
    SampleMetric,
    SamplePayload,
    SourceIdentity,
    TransformationProvenance,
    Unit,
)

CONDITION_TARGET = re.compile(r"^[A-Z][A-Z0-9_]*$")


class MappingStatus(StrEnum):
    MAPPED = "MAPPED"
    UNSUPPORTED_DATA_ITEM = "UNSUPPORTED_DATA_ITEM"
    UNKNOWN_DATA_ITEM = "UNKNOWN_DATA_ITEM"
    AMBIGUOUS_DATA_ITEM = "AMBIGUOUS_DATA_ITEM"
    INVALID_RAW_RECORD = "INVALID_RAW_RECORD"
    INVALID_VALUE = "INVALID_VALUE"


@dataclass(frozen=True, slots=True)
class CatalogDataItem:
    data_item_id: str
    component_id: str
    name: str
    category: str
    type: str
    subtype: str | None
    unit: str | None


@dataclass(frozen=True, slots=True)
class CatalogSnapshot:
    machine_id: str
    data_items: tuple[CatalogDataItem, ...]

    def by_name(self) -> dict[str, tuple[CatalogDataItem, ...]]:
        grouped: dict[str, list[CatalogDataItem]] = {}
        for item in self.data_items:
            grouped.setdefault(item.name, []).append(item)
        return {name: tuple(items) for name, items in grouped.items()}

    def by_id(self) -> dict[str, CatalogDataItem]:
        return {item.data_item_id: item for item in self.data_items}


@dataclass(frozen=True, slots=True)
class MappingDefinition:
    data_item: CatalogDataItem
    target: str


@dataclass(frozen=True, slots=True)
class MappingTable:
    mapping_version: str
    source_set_id: str
    machine_id: str
    devices_artifact_id: str
    raw_artifact_id: str
    entries: tuple[MappingDefinition, ...]
    checksum: str

    def by_data_item_id(self) -> dict[str, MappingDefinition]:
        return {entry.data_item.data_item_id: entry for entry in self.entries}

    def validate_catalog(self, catalog: CatalogSnapshot) -> None:
        if catalog.machine_id != self.machine_id:
            raise ValueError("Mapping table machineId does not match the catalog")
        catalog_by_id = catalog.by_id()
        for definition in self.entries:
            actual = catalog_by_id.get(definition.data_item.data_item_id)
            if actual is None:
                raise ValueError(
                    f"Mapped DataItem is absent from catalog: {definition.data_item.data_item_id}"
                )
            if actual != definition.data_item:
                raise ValueError(
                    "Mapped DataItem metadata differs from catalog: "
                    f"{definition.data_item.data_item_id}"
                )
            _validate_target(definition)
        _validate_unique_observation_channels(self.entries)


@dataclass(frozen=True, slots=True)
class MappingCandidate:
    artifact_id: str
    raw_record_id: str
    source_event_key: str
    line_number: int
    timestamp_raw: str | None
    value_fields_raw: tuple[str, ...]
    parse_status: str
    parse_error: str | None
    data_item: CatalogDataItem | None
    data_item_name_raw: str | None


@dataclass(frozen=True, slots=True)
class MappingResult:
    status: MappingStatus
    candidate: MappingCandidate
    observation: ObservationEnvelope | None = None
    reason: str | None = None


@dataclass(frozen=True, slots=True)
class MappingContext:
    source_set_id: str
    machine_id: str
    mapping_version: str


class ObservationMapper:
    def map(
        self,
        candidate: MappingCandidate,
        definition: MappingDefinition | None,
        event_id: UUID | None,
        context: MappingContext,
    ) -> MappingResult:
        early_result = _classify_unmappable(candidate, definition)
        if early_result is not None:
            return early_result
        if event_id is None or definition is None or candidate.data_item is None:
            raise ValueError("Mapped candidate requires definition, catalog metadata, and eventId")
        try:
            payload = _map_payload(candidate, definition)
            observed_at = _parse_source_time(candidate.timestamp_raw)
            observation = ObservationEnvelope(
                event_id=event_id,
                source_event_key=candidate.source_event_key,
                machine_id=context.machine_id,
                subject=ObservationSubject(component_id=definition.data_item.component_id),
                source=SourceIdentity(source_observed_at=observed_at),
                provenance=Provenance(
                    source=ProvenanceSource(
                        source_set_id=context.source_set_id,
                        artifact_id=candidate.artifact_id,
                    ),
                    transformation=TransformationProvenance(
                        raw_record_id=candidate.raw_record_id,
                        mapping_version=context.mapping_version,
                        source_data_item_id=definition.data_item.data_item_id,
                    ),
                ),
                payload=payload,
            )
        except (ValueError, OverflowError) as error:
            return MappingResult(
                MappingStatus.INVALID_VALUE,
                candidate,
                reason=type(error).__name__,
            )
        return MappingResult(MappingStatus.MAPPED, candidate, observation=observation)


def _classify_unmappable(
    candidate: MappingCandidate, definition: MappingDefinition | None
) -> MappingResult | None:
    status_by_parse_status = {
        "INVALID": MappingStatus.INVALID_RAW_RECORD,
        "UNKNOWN_DATA_ITEM": MappingStatus.UNKNOWN_DATA_ITEM,
        "AMBIGUOUS_DATA_ITEM": MappingStatus.AMBIGUOUS_DATA_ITEM,
    }
    if candidate.parse_status in status_by_parse_status:
        return MappingResult(
            status_by_parse_status[candidate.parse_status],
            candidate,
            reason=candidate.parse_error,
        )
    if definition is None:
        return MappingResult(
            MappingStatus.UNSUPPORTED_DATA_ITEM,
            candidate,
            reason="NO_MAPPING_DEFINITION",
        )
    if candidate.data_item != definition.data_item:
        return MappingResult(
            MappingStatus.INVALID_RAW_RECORD,
            candidate,
            reason="CATALOG_METADATA_MISMATCH",
        )
    return None


def _map_payload(
    candidate: MappingCandidate, definition: MappingDefinition
) -> SamplePayload | EventPayload | ConditionPayload:
    category = definition.data_item.category
    if category == "SAMPLE":
        return _map_sample(candidate, definition)
    if category == "EVENT":
        return _map_event(candidate, definition)
    if category == "CONDITION":
        return _map_condition(candidate, definition)
    raise ValueError("Unsupported observation category")


def _map_sample(candidate: MappingCandidate, definition: MappingDefinition) -> SamplePayload:
    raw_value = _single_value(candidate)
    metric = SampleMetric(definition.target)
    if _is_unavailable(raw_value):
        return SamplePayload(metric=metric, availability=Availability.UNAVAILABLE)
    value = float(raw_value)
    if not math.isfinite(value):
        raise ValueError("Sample value must be finite")
    if definition.data_item.unit is None:
        raise ValueError("Available sample requires catalog unit")
    return SamplePayload(
        metric=metric,
        availability=Availability.AVAILABLE,
        value=value,
        unit=Unit(definition.data_item.unit),
    )


def _map_event(candidate: MappingCandidate, definition: MappingDefinition) -> EventPayload:
    raw_value = _single_value(candidate)
    event_type = EventType(definition.target)
    if _is_unavailable(raw_value):
        return EventPayload(event_type=event_type, availability=Availability.UNAVAILABLE)
    value: str | int = raw_value
    if event_type in {EventType.TOOL_NUMBER, EventType.PART_COUNT}:
        value = int(raw_value)
    return EventPayload(
        event_type=event_type,
        availability=Availability.AVAILABLE,
        value=value,
    )


def _map_condition(candidate: MappingCandidate, definition: MappingDefinition) -> ConditionPayload:
    if len(candidate.value_fields_raw) != 5:
        raise ValueError("Condition requires five source fields")
    level_raw, native_code, native_severity, qualifier, message = candidate.value_fields_raw
    return ConditionPayload(
        condition_type=definition.target,
        level=ConditionLevel(level_raw.upper()),
        native_code=native_code or None,
        native_severity=native_severity or None,
        qualifier=qualifier or None,
        message=message or None,
    )


def _single_value(candidate: MappingCandidate) -> str:
    if len(candidate.value_fields_raw) != 1:
        raise ValueError("Sample and Event require one source value")
    return candidate.value_fields_raw[0]


def _is_unavailable(value: str) -> bool:
    return value.casefold() == "unavailable"


def _parse_source_time(value: str | None) -> datetime:
    if value is None:
        raise ValueError("Mapped record requires source timestamp")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    observed_at = datetime.fromisoformat(normalized)
    if observed_at.tzinfo is None or observed_at.utcoffset() is None:
        raise ValueError("Source timestamp requires timezone")
    return observed_at


def _validate_target(definition: MappingDefinition) -> None:
    category = definition.data_item.category
    if category == "SAMPLE":
        metric = SampleMetric(definition.target)
        unit = Unit(definition.data_item.unit or "")
        if EXPECTED_UNITS[metric] is not unit:
            raise ValueError(f"Mapping target unit differs: {definition.data_item.data_item_id}")
        return
    if category == "EVENT":
        EventType(definition.target)
        if definition.data_item.unit is not None:
            raise ValueError(
                f"Event mapping must not declare unit: {definition.data_item.data_item_id}"
            )
        return
    if category == "CONDITION":
        if not CONDITION_TARGET.fullmatch(definition.target):
            raise ValueError(f"Invalid Condition target: {definition.data_item.data_item_id}")
        return
    raise ValueError(f"Unsupported mapping category: {category}")


def _validate_unique_observation_channels(entries: tuple[MappingDefinition, ...]) -> None:
    channels: set[tuple[str, str, str]] = set()
    for definition in entries:
        channel = (
            definition.data_item.component_id,
            definition.data_item.category,
            definition.target,
        )
        if channel in channels:
            raise ValueError(
                "Mapping table contains duplicate component/category/target channel: "
                f"{definition.data_item.component_id}/{definition.data_item.category}/"
                f"{definition.target}"
            )
        channels.add(channel)
