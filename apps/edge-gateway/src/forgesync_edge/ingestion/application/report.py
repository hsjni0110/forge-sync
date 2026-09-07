"""Deterministic, payload-safe semantic mapping report aggregation."""

from __future__ import annotations

from collections import Counter
from typing import Any

from ..domain.mapping import CatalogDataItem, MappingResult, MappingStatus, MappingTable
from ..domain.observation import EventPayload, SamplePayload

REPORT_SCHEMA_VERSION = "2.1.0"
ISSUE_SAMPLE_LIMIT = 25


class MappingReportBuilder:
    def __init__(
        self,
        table: MappingTable,
        processing_run_id: str,
        parser_version: str,
        mapper_version: str,
    ) -> None:
        self._table = table
        self._processing_run_id = processing_run_id
        self._parser_version = parser_version
        self._mapper_version = mapper_version
        self._status_counts: Counter[str] = Counter()
        self._item_status_counts: dict[str, Counter[str]] = {}
        self._item_unavailable_counts: Counter[str] = Counter()
        self._item_metadata: dict[str, dict[str, object]] = {}
        self._first_locators: dict[tuple[str, str], str] = {}
        self._failure_samples: list[dict[str, object]] = []

    def add(self, result: MappingResult) -> None:
        status = result.status.value
        self._status_counts[status] += 1
        item_key = _item_key(result)
        self._item_status_counts.setdefault(item_key, Counter())[status] += 1
        self._item_metadata.setdefault(item_key, _item_metadata(result))
        self._first_locators.setdefault((status, item_key), result.candidate.raw_record_id)
        payload = result.observation.payload if result.observation is not None else None
        if (
            isinstance(payload, (SamplePayload, EventPayload))
            and payload.availability.value == "UNAVAILABLE"
        ):
            self._item_unavailable_counts[item_key] += 1
        if (
            status
            in {
                MappingStatus.INVALID_RAW_RECORD.value,
                MappingStatus.INVALID_VALUE.value,
            }
            and len(self._failure_samples) < ISSUE_SAMPLE_LIMIT
        ):
            self._failure_samples.append(
                {
                    "status": status,
                    "reason": result.reason,
                    "rawRecordId": result.candidate.raw_record_id,
                    "dataItemName": result.candidate.data_item_name_raw,
                }
            )

    def build(self) -> dict[str, Any]:
        total = sum(self._status_counts.values())
        invalid_raw = self._status_counts[MappingStatus.INVALID_RAW_RECORD.value]
        parsed = total - invalid_raw
        mapped = self._status_counts[MappingStatus.MAPPED.value]
        return {
            "reportSchemaVersion": REPORT_SCHEMA_VERSION,
            "mappingVersion": self._table.mapping_version,
            "mapperVersion": self._mapper_version,
            "parserVersion": self._parser_version,
            "processingRunId": self._processing_run_id,
            "source": {
                "sourceSetId": self._table.source_set_id,
                "machineId": self._table.machine_id,
                "devicesArtifactId": self._table.devices_artifact_id,
                "rawArtifactId": self._table.raw_artifact_id,
                "mappingTableSha256": self._table.checksum,
            },
            "records": {
                "total": total,
                "syntacticallyParsed": parsed,
                "mapped": mapped,
                "byStatus": dict(sorted(self._status_counts.items())),
            },
            "semanticCoverage": {
                "definition": "mapped records / syntactically parsed records",
                "mappedRecords": mapped,
                "parsedRecords": parsed,
                "ratio": mapped / parsed if parsed else None,
            },
            "mappings": self._mapped_items(),
            "unsupportedDataItems": self._items_for_status(MappingStatus.UNSUPPORTED_DATA_ITEM),
            "unknownDataItems": self._items_for_status(MappingStatus.UNKNOWN_DATA_ITEM),
            "ambiguousDataItems": self._items_for_status(MappingStatus.AMBIGUOUS_DATA_ITEM),
            "failureSamples": self._failure_samples,
            "limitations": [
                "Unsupported DataItems remain traceable Raw Records and are not Canonical metrics.",
                "Telemetry PART_COUNT is not a ProductionResult.",
                "Cload is deferred because it collides with Sload on the same component "
                "LOAD channel.",
                "A derived unit is the reviewed unit of a canonical target whose catalog "
                "declares none. It is evidence, not a source declaration.",
                "Catalog units on Event DataItems stay in the Devices artifact; canonical Event "
                "payloads carry no unit.",
            ],
        }

    def _mapped_items(self) -> list[dict[str, object]]:
        definitions = self._table.by_data_item_id()
        rows: list[dict[str, object]] = []
        for data_item_id in sorted(definitions):
            definition = definitions[data_item_id]
            counts = self._item_status_counts.get(data_item_id, Counter())
            rows.append(
                {
                    **_catalog_metadata(definition.data_item),
                    "derivedUnit": definition.derived_unit,
                    "target": definition.target,
                    "mappedRecords": counts[MappingStatus.MAPPED.value],
                    "unavailableRecords": self._item_unavailable_counts[data_item_id],
                    "invalidValueRecords": counts[MappingStatus.INVALID_VALUE.value],
                }
            )
        return rows

    def _items_for_status(self, status: MappingStatus) -> list[dict[str, object]]:
        rows: list[dict[str, object]] = []
        for item_key in sorted(self._item_status_counts):
            count = self._item_status_counts[item_key][status.value]
            if not count:
                continue
            rows.append(
                {
                    **self._item_metadata[item_key],
                    "recordCount": count,
                    "firstRawRecordId": self._first_locators[(status.value, item_key)],
                }
            )
        return rows


def render_mapping_report(report: dict[str, Any]) -> str:
    records = report["records"]
    coverage = report["semanticCoverage"]
    lines = [
        "# Mazak01 Canonical Mapping Report",
        "",
        "## Reproducibility",
        "",
        f"- Processing run: `{report['processingRunId']}`",
        f"- Mapping / Mapper / Parser: `{report['mappingVersion']}` / "
        f"`{report['mapperVersion']}` / `{report['parserVersion']}`",
        f"- Raw artifact: `{report['source']['rawArtifactId']}`",
        f"- Mapping table SHA-256: `{report['source']['mappingTableSha256']}`",
        "",
        "## Semantic coverage",
        "",
        f"- Mapped / parsed: {coverage['mappedRecords']} / {coverage['parsedRecords']} "
        f"({_format_ratio(coverage['ratio'])})",
        f"- Status counts: `{records['byStatus']}`",
        "",
        "## Explicit mappings",
        "",
        "| DataItem | Component | Category | Source type | Unit | Target | Mapped | "
        "Unavailable | Invalid |",
        "|---|---|---|---|---|---|---:|---:|---:|",
    ]
    for mapping in report["mappings"]:
        lines.append(
            f"| `{mapping['name']}` | `{mapping['componentId']}` | {mapping['category']} | "
            f"`{mapping['type']}` | {_format_unit(mapping)} | "
            f"`{mapping['target']}` | {mapping['mappedRecords']} | "
            f"{mapping['unavailableRecords']} | {mapping['invalidValueRecords']} |"
        )
    lines.extend(_unmapped_section("Unsupported DataItems", report["unsupportedDataItems"]))
    lines.extend(_unmapped_section("Unknown DataItems", report["unknownDataItems"]))
    lines.extend(
        [
            "",
            "## Limitations",
            "",
            *[f"- {limitation}" for limitation in report["limitations"]],
            "",
        ]
    )
    return "\n".join(lines)


def _item_key(result: MappingResult) -> str:
    if result.candidate.data_item is not None:
        return result.candidate.data_item.data_item_id
    return result.candidate.data_item_name_raw or "<MISSING>"


def _item_metadata(result: MappingResult) -> dict[str, object]:
    if result.candidate.data_item is not None:
        return _catalog_metadata(result.candidate.data_item)
    return {"name": result.candidate.data_item_name_raw or "<MISSING>"}


def _catalog_metadata(data_item: CatalogDataItem) -> dict[str, object]:
    return {
        "dataItemId": data_item.data_item_id,
        "componentId": data_item.component_id,
        "name": data_item.name,
        "category": data_item.category,
        "type": data_item.type,
        "subType": data_item.subtype,
        "unit": data_item.unit,
    }


def _unmapped_section(title: str, items: list[dict[str, object]]) -> list[str]:
    lines = ["", f"## {title}", "", "| DataItem | Records | First Raw Record |", "|---|---:|---|"]
    for item in items:
        lines.append(f"| `{item['name']}` | {item['recordCount']} | `{item['firstRawRecordId']}` |")
    if not items:
        lines.append("| - | 0 | - |")
    return lines


def _format_unit(mapping: dict[str, object]) -> str:
    if mapping["unit"] is not None:
        return f"`{mapping['unit']}`"
    if mapping["derivedUnit"] is not None:
        return f"`{mapping['derivedUnit']}` (derived)"
    return "-"


def _format_ratio(value: float | None) -> str:
    return "-" if value is None else f"{value:.2%}"
