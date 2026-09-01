"""Framework-independent models for source acquisition and raw decoding."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import Any


class ArtifactRole(StrEnum):
    MTCONNECT_DEVICES = "MTCONNECT_DEVICES"
    SHDR_RAW = "SHDR_RAW"


class AcquisitionStatus(StrEnum):
    STORED = "STORED"
    REUSED_VERIFIED = "REUSED_VERIFIED"


class ParseStatus(StrEnum):
    PARSED = "PARSED"
    UNKNOWN_DATA_ITEM = "UNKNOWN_DATA_ITEM"
    AMBIGUOUS_DATA_ITEM = "AMBIGUOUS_DATA_ITEM"
    INVALID = "INVALID"


@dataclass(frozen=True, slots=True)
class SourceArtifactSpec:
    alias: str
    role: ArtifactRole
    repository_path: str
    source_uri: str
    media_type: str
    expected_byte_length: int
    sha256: str

    @property
    def artifact_id(self) -> str:
        return f"sha256:{self.sha256}"


@dataclass(frozen=True, slots=True)
class SourceLock:
    source_set_id: str
    source_name: str
    upstream_repository: str
    upstream_commit: str
    license_or_terms_reference: str
    evidence_state: str
    artifacts: tuple[SourceArtifactSpec, ...]

    def artifact_for_role(self, role: ArtifactRole) -> SourceArtifactSpec:
        matching = tuple(artifact for artifact in self.artifacts if artifact.role is role)
        if len(matching) != 1:
            raise ValueError(f"Expected exactly one {role.value} artifact, found {len(matching)}")
        return matching[0]


@dataclass(frozen=True, slots=True)
class SourceArtifact:
    artifact_id: str
    alias: str
    payload_path: Path
    manifest_path: Path
    byte_length: int
    sha256: str
    acquisition_status: AcquisitionStatus


@dataclass(frozen=True, slots=True)
class DataItemCatalogEntry:
    data_item_id: str
    component_id: str
    name: str
    category: str
    type: str
    subtype: str | None
    units: str | None
    native_units: str | None
    coordinate_system: str | None
    component_path: tuple[str, ...]
    source_text: str | None

    def to_dict(self) -> dict[str, Any]:
        return {
            "dataItemId": self.data_item_id,
            "componentId": self.component_id,
            "name": self.name,
            "category": self.category,
            "type": self.type,
            "subType": self.subtype,
            "units": self.units,
            "nativeUnits": self.native_units,
            "coordinateSystem": self.coordinate_system,
            "componentPath": list(self.component_path),
            "sourceText": self.source_text,
        }


@dataclass(frozen=True, slots=True)
class MachineCatalog:
    machine_id: str
    machine_name: str
    uuid: str | None
    description: dict[str, str | None]
    entries: tuple[DataItemCatalogEntry, ...]

    def entries_by_name(self) -> dict[str, tuple[DataItemCatalogEntry, ...]]:
        grouped: dict[str, list[DataItemCatalogEntry]] = {}
        for entry in self.entries:
            grouped.setdefault(entry.name, []).append(entry)
        return {name: tuple(items) for name, items in grouped.items()}


@dataclass(frozen=True, slots=True)
class RawRecord:
    artifact_id: str
    line_number: int
    byte_start: int
    byte_end: int
    raw_payload_ref: str
    raw_line_sha256: str
    timestamp_raw: str | None
    data_item_name_raw: str | None
    value_fields_raw: tuple[str, ...]
    parse_status: ParseStatus
    parse_error: str | None
    parser_version: str
    category: str | None

    def locator(self) -> dict[str, int | str]:
        return {
            "artifactId": self.artifact_id,
            "lineNumber": self.line_number,
            "byteStart": self.byte_start,
            "byteEnd": self.byte_end,
        }
