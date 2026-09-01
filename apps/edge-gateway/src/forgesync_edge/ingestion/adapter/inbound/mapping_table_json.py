"""Strict JSON adapter for reviewed semantic mapping definitions."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from ...domain.mapping import CatalogDataItem, MappingDefinition, MappingTable

ROOT_FIELDS = {
    "mappingVersion",
    "sourceSetId",
    "machineId",
    "devicesArtifactId",
    "rawArtifactId",
    "entries",
}
ENTRY_FIELDS = {
    "dataItemId",
    "componentId",
    "name",
    "category",
    "type",
    "subType",
    "unit",
    "target",
}
SEMANTIC_VERSION = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
SHA256_ID = re.compile(r"^sha256:[0-9a-f]{64}$")


def load_mapping_table(path: Path) -> MappingTable:
    encoded = path.read_bytes()
    try:
        document = json.loads(encoded)
    except json.JSONDecodeError as error:
        raise ValueError(f"Cannot parse mapping table: {error.msg}") from error
    root = _object(document, "mapping table")
    _require_exact_fields(root, ROOT_FIELDS, "mapping table")
    mapping_version = _string(root["mappingVersion"], "mappingVersion")
    if not SEMANTIC_VERSION.fullmatch(mapping_version):
        raise ValueError("mappingVersion must use semantic version format")
    devices_artifact_id = _artifact_id(root["devicesArtifactId"], "devicesArtifactId")
    raw_artifact_id = _artifact_id(root["rawArtifactId"], "rawArtifactId")
    entries_value = root["entries"]
    if not isinstance(entries_value, list) or not entries_value:
        raise ValueError("entries must be a non-empty array")
    entries = tuple(_entry(value, index) for index, value in enumerate(entries_value))
    ids = [entry.data_item.data_item_id for entry in entries]
    if len(ids) != len(set(ids)):
        raise ValueError("mapping table contains duplicate dataItemId")
    return MappingTable(
        mapping_version=mapping_version,
        source_set_id=_string(root["sourceSetId"], "sourceSetId"),
        machine_id=_string(root["machineId"], "machineId"),
        devices_artifact_id=devices_artifact_id,
        raw_artifact_id=raw_artifact_id,
        entries=entries,
        checksum=hashlib.sha256(encoded).hexdigest(),
    )


def _entry(value: object, index: int) -> MappingDefinition:
    label = f"entries[{index}]"
    entry = _object(value, label)
    _require_exact_fields(entry, ENTRY_FIELDS, label)
    subtype = _nullable_string(entry["subType"], f"{label}.subType")
    unit = _nullable_string(entry["unit"], f"{label}.unit")
    return MappingDefinition(
        data_item=CatalogDataItem(
            data_item_id=_string(entry["dataItemId"], f"{label}.dataItemId"),
            component_id=_string(entry["componentId"], f"{label}.componentId"),
            name=_string(entry["name"], f"{label}.name"),
            category=_string(entry["category"], f"{label}.category"),
            type=_string(entry["type"], f"{label}.type"),
            subtype=subtype,
            unit=unit,
        ),
        target=_string(entry["target"], f"{label}.target"),
    )


def _object(value: object, label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or not all(isinstance(key, str) for key in value):
        raise ValueError(f"{label} must be an object")
    return value


def _require_exact_fields(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise ValueError(f"{label} fields differ: missing={missing}, unknown={unknown}")


def _string(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{label} must be a non-empty string")
    return value


def _nullable_string(value: object, label: str) -> str | None:
    if value is None:
        return None
    return _string(value, label)


def _artifact_id(value: object, label: str) -> str:
    artifact_id = _string(value, label)
    if not SHA256_ID.fullmatch(artifact_id):
        raise ValueError(f"{label} must be a SHA-256 artifact identity")
    return artifact_id
