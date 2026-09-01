"""Deterministic machine source profile generation and rendering."""

from __future__ import annotations

import hashlib
import json
from collections import Counter, defaultdict
from collections.abc import Iterable
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from .domain import MachineCatalog, ParseStatus, RawRecord, SourceArtifactSpec, SourceLock
from .shdr_decoder import PARSER_VERSION, parse_source_timestamp

PROFILE_SCHEMA_VERSION = "1.0.0"
SAMPLE_LIMIT = 5
ISSUE_SAMPLE_LIMIT = 25


@dataclass
class _ItemStats:
    record_count: int = 0
    unavailable_count: int = 0
    sample_values: list[dict[str, object]] = field(default_factory=list)


class SourceProfileGenerator:
    def generate(
        self,
        source_lock: SourceLock,
        catalog: MachineCatalog,
        records: Iterable[RawRecord],
        devices_spec: SourceArtifactSpec,
        raw_spec: SourceArtifactSpec,
    ) -> dict[str, Any]:
        status_counts: Counter[str] = Counter()
        category_counts: Counter[str] = Counter()
        item_stats: defaultdict[str, _ItemStats] = defaultdict(_ItemStats)
        unknown_stats: dict[str, dict[str, object]] = {}
        ambiguous_stats: dict[str, dict[str, object]] = {}
        invalid_samples: list[dict[str, object]] = []
        ordering_samples: list[dict[str, object]] = []
        unavailable_count = 0
        first_observed_at: datetime | None = None
        last_observed_at: datetime | None = None
        previous_observed_at: datetime | None = None
        ordering_anomaly_count = 0

        for record in records:
            status_counts[record.parse_status.value] += 1
            if record.category:
                category_counts[record.category] += 1
            if record.timestamp_raw and record.parse_status is not ParseStatus.INVALID:
                observed_at = parse_source_timestamp(record.timestamp_raw)
                first_observed_at = (
                    min(first_observed_at, observed_at) if first_observed_at else observed_at
                )
                last_observed_at = (
                    max(last_observed_at, observed_at) if last_observed_at else observed_at
                )
                if previous_observed_at and observed_at < previous_observed_at:
                    ordering_anomaly_count += 1
                    if len(ordering_samples) < ISSUE_SAMPLE_LIMIT:
                        ordering_samples.append(
                            {
                                "previousTimestamp": previous_observed_at.isoformat(),
                                "currentTimestamp": observed_at.isoformat(),
                                "locator": record.locator(),
                            }
                        )
                previous_observed_at = observed_at
            if record.parse_status is ParseStatus.INVALID:
                if len(invalid_samples) < ISSUE_SAMPLE_LIMIT:
                    invalid_samples.append(_issue_sample(record))
                continue
            if record.parse_status is ParseStatus.UNKNOWN_DATA_ITEM:
                _accumulate_issue(unknown_stats, record)
                continue
            if record.parse_status is ParseStatus.AMBIGUOUS_DATA_ITEM:
                _accumulate_issue(ambiguous_stats, record)
                continue
            if not record.data_item_name_raw:
                continue
            stats = item_stats[record.data_item_name_raw]
            stats.record_count += 1
            if record.value_fields_raw and record.value_fields_raw[0].casefold() == "unavailable":
                stats.unavailable_count += 1
                unavailable_count += 1
            if len(stats.sample_values) < SAMPLE_LIMIT:
                stats.sample_values.append(
                    {
                        "valueFieldsRaw": list(record.value_fields_raw),
                        "timestampRaw": record.timestamp_raw,
                        "locator": record.locator(),
                    }
                )

        syntactically_parsed = (
            status_counts[ParseStatus.PARSED.value]
            + status_counts[ParseStatus.UNKNOWN_DATA_ITEM.value]
            + status_counts[ParseStatus.AMBIGUOUS_DATA_ITEM.value]
        )
        known_records = status_counts[ParseStatus.PARSED.value]
        data_items = self._data_item_profiles(catalog, item_stats)
        return {
            "profileSchemaVersion": PROFILE_SCHEMA_VERSION,
            "parserVersion": PARSER_VERSION,
            "processingRunId": _processing_run_id(
                source_lock, catalog.machine_id, devices_spec, raw_spec
            ),
            "sourceSet": {
                "sourceSetId": source_lock.source_set_id,
                "sourceName": source_lock.source_name,
                "upstreamRepository": source_lock.upstream_repository,
                "upstreamCommit": source_lock.upstream_commit,
                "licenseOrTermsReference": source_lock.license_or_terms_reference,
                "artifacts": [
                    _artifact_profile(devices_spec),
                    _artifact_profile(raw_spec),
                ],
            },
            "machine": {
                "id": catalog.machine_id,
                "name": catalog.machine_name,
                "uuid": catalog.uuid,
                "description": catalog.description,
                "dataItemCount": len(catalog.entries),
            },
            "records": {
                "total": sum(status_counts.values()),
                "byStatus": dict(sorted(status_counts.items())),
                "byCategory": dict(sorted(category_counts.items())),
                "unavailable": unavailable_count,
            },
            "observedTimeRange": {
                "first": first_observed_at.isoformat() if first_observed_at else None,
                "last": last_observed_at.isoformat() if last_observed_at else None,
            },
            "semanticCoverage": {
                "definition": "catalog-matched records / syntactically parsed records",
                "mappedRecords": known_records,
                "parsedRecords": syntactically_parsed,
                "ratio": known_records / syntactically_parsed if syntactically_parsed else None,
                "canonicalMappingEvaluated": False,
            },
            "ordering": {
                "anomalyCount": ordering_anomaly_count,
                "samples": ordering_samples,
            },
            "dataItems": data_items,
            "unknownDataItems": _sorted_issue_profiles(unknown_stats),
            "ambiguousDataItems": _sorted_issue_profiles(ambiguous_stats),
            "invalidRecords": {
                "count": status_counts[ParseStatus.INVALID.value],
                "samples": invalid_samples,
            },
            "limitations": [
                "Raw values are preserved as strings; canonical numeric and enum mapping "
                "is not evaluated.",
                "Coverage means catalog matching only, not canonical observation coverage.",
                "The report covers one selected Mazak01 daily raw artifact, not the full corpus.",
            ],
        }

    @staticmethod
    def _data_item_profiles(
        catalog: MachineCatalog, item_stats: defaultdict[str, _ItemStats]
    ) -> list[dict[str, object]]:
        name_counts = Counter(entry.name for entry in catalog.entries)
        profiles: list[dict[str, object]] = []
        for entry in catalog.entries:
            stats = item_stats[entry.name] if name_counts[entry.name] == 1 else _ItemStats()
            profile = entry.to_dict()
            profile.update(
                {
                    "recordCount": stats.record_count,
                    "unavailableCount": stats.unavailable_count,
                    "sampleValues": stats.sample_values,
                    "ambiguousName": name_counts[entry.name] > 1,
                }
            )
            profiles.append(profile)
        return profiles


def write_profile(profile: dict[str, Any], output_directory: Path) -> tuple[Path, Path]:
    output_directory.mkdir(parents=True, exist_ok=True)
    json_path = output_directory / "profile.json"
    markdown_path = output_directory / "profile.md"
    json_path.write_text(
        json.dumps(profile, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    markdown_path.write_text(render_markdown(profile), encoding="utf-8")
    return json_path, markdown_path


def render_markdown(profile: dict[str, Any]) -> str:
    machine = profile["machine"]
    records = profile["records"]
    coverage = profile["semanticCoverage"]
    time_range = profile["observedTimeRange"]
    source_set = profile["sourceSet"]
    lines = [
        f"# {machine['name']} Source Profile",
        "",
        "## 출처와 재현성",
        "",
        f"- Source: {source_set['sourceName']}",
        f"- Upstream commit: `{source_set['upstreamCommit']}`",
        f"- Processing run: `{profile['processingRunId']}`",
        f"- Parser/Profile schema: `{profile['parserVersion']}` / "
        f"`{profile['profileSchemaVersion']}`",
        f"- Terms: {source_set['licenseOrTermsReference']}",
        "- Acknowledgement: Source data and metadata originate from NIST. "
        "No NIST endorsement is implied and the NIST logo is not used.",
        "",
        "## Machine",
        "",
        f"- ID / Name: `{machine['id']}` / `{machine['name']}`",
        f"- UUID: `{machine['uuid']}`",
        f"- Description: {machine['description'].get('text') or '없음'}",
        f"- DataItems: {machine['dataItemCount']}",
        "",
        "## Raw record 요약",
        "",
        f"- 전체: {records['total']}",
        f"- 상태별: `{json.dumps(records['byStatus'], sort_keys=True)}`",
        f"- category별: `{json.dumps(records['byCategory'], sort_keys=True)}`",
        f"- UNAVAILABLE: {records['unavailable']}",
        f"- Source time: `{time_range['first']}` ~ `{time_range['last']}`",
        f"- Catalog coverage: {coverage['mappedRecords']} / {coverage['parsedRecords']} "
        f"({_format_ratio(coverage['ratio'])})",
        f"- Ordering anomalies: {profile['ordering']['anomalyCount']}",
        "",
        "## DataItem catalog와 관찰 수",
        "",
        "| Name | Category | Type | Unit | Records | Unavailable | Ambiguous |",
        "|---|---|---|---|---:|---:|---|",
    ]
    for item in profile["dataItems"]:
        lines.append(
            f"| `{item['name']}` | {item['category']} | `{item['type']}` | "
            f"`{item['units'] or '-'}` | {item['recordCount']} | "
            f"{item['unavailableCount']} | {'YES' if item['ambiguousName'] else 'NO'} |"
        )
    lines.extend(
        [
            "",
            "## 검토가 필요한 항목",
            "",
            f"- Unknown DataItems: {len(profile['unknownDataItems'])}종",
            f"- Ambiguous DataItems: {len(profile['ambiguousDataItems'])}종",
            f"- Invalid records: {profile['invalidRecords']['count']}건",
            "- Canonical observation mapping: 아직 평가하지 않음",
            "",
            "### Unknown DataItems",
            "",
            "| Name | Records | First locator |",
            "|---|---:|---|",
        ]
    )
    for item in profile["unknownDataItems"]:
        first_locator = _format_locator(item["samples"][0]["locator"]) if item["samples"] else "-"
        lines.append(f"| `{item['name']}` | {item['count']} | `{first_locator}` |")
    if not profile["unknownDataItems"]:
        lines.append("| - | 0 | - |")
    lines.extend(
        [
            "",
            "## 제한사항",
            "",
            *[f"- {limitation}" for limitation in profile["limitations"]],
            "",
            "## 재생성",
            "",
            "```bash",
            "uv run forgesync-source profile \\",
            "  --lock config/sources/nist-mazak01-20161005.lock.json \\",
            "  --store datasets/raw \\",
            "  --machine Mazak01 \\",
            "  --output docs/data/profiles/nist-mazak01-20161005",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def _processing_run_id(
    source_lock: SourceLock,
    machine_id: str,
    devices_spec: SourceArtifactSpec,
    raw_spec: SourceArtifactSpec,
) -> str:
    identity = "\n".join(
        (
            source_lock.source_set_id,
            devices_spec.sha256,
            raw_spec.sha256,
            machine_id,
            PARSER_VERSION,
            PROFILE_SCHEMA_VERSION,
        )
    )
    return f"sha256:{hashlib.sha256(identity.encode()).hexdigest()}"


def _artifact_profile(spec: SourceArtifactSpec) -> dict[str, object]:
    return {
        "alias": spec.alias,
        "role": spec.role.value,
        "artifactId": spec.artifact_id,
        "repositoryPath": spec.repository_path,
        "sourceUri": spec.source_uri,
        "mediaType": spec.media_type,
        "byteLength": spec.expected_byte_length,
        "sha256": spec.sha256,
    }


def _issue_sample(record: RawRecord) -> dict[str, object]:
    return {
        "name": record.data_item_name_raw,
        "timestampRaw": record.timestamp_raw,
        "valueFieldsRaw": list(record.value_fields_raw),
        "error": record.parse_error,
        "locator": record.locator(),
    }


def _accumulate_issue(target: dict[str, dict[str, object]], record: RawRecord) -> None:
    name = record.data_item_name_raw or "<missing>"
    aggregate = target.setdefault(name, {"name": name, "count": 0, "samples": []})
    current_count = aggregate["count"]
    if not isinstance(current_count, int):
        raise TypeError("Issue aggregate count must be an integer")
    aggregate["count"] = current_count + 1
    samples = aggregate["samples"]
    if isinstance(samples, list) and len(samples) < SAMPLE_LIMIT:
        samples.append(_issue_sample(record))


def _sorted_issue_profiles(target: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    return [target[name] for name in sorted(target)]


def _format_ratio(value: float | None) -> str:
    return "N/A" if value is None else f"{value:.2%}"


def _format_locator(value: object) -> str:
    if not isinstance(value, dict):
        return str(value)
    artifact_id = value.get("artifactId", "unknown")
    line_number = value.get("lineNumber", "unknown")
    byte_start = value.get("byteStart", "unknown")
    byte_end = value.get("byteEnd", "unknown")
    return f"{artifact_id}#line={line_number};bytes={byte_start}-{byte_end}"
