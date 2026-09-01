"""Immutable filesystem output for L2 observations and human-reviewable reports."""

from __future__ import annotations

import hashlib
import json
import tempfile
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

from ...application.report import MappingReportBuilder, render_mapping_report
from ...application.run_identity import MAPPER_VERSION
from ...domain.mapping import MappingResult, MappingTable
from .observation_json import serialize_observation


@dataclass(frozen=True, slots=True)
class CanonicalRunOutput:
    processing_run_id: str
    run_directory: Path
    observation_count: int
    status: str
    report: dict[str, object]


def write_canonical_run(
    results: Iterable[MappingResult],
    table: MappingTable,
    processing_run_id: str,
    parser_version: str,
    canonical_store: Path,
) -> CanonicalRunOutput:
    source_directory = canonical_store / table.source_set_id
    source_directory.mkdir(parents=True, exist_ok=True)
    run_hash = processing_run_id.removeprefix("sha256:")
    final_directory = source_directory / run_hash
    with tempfile.TemporaryDirectory(prefix=".mapping-", dir=source_directory) as temporary:
        temporary_directory = Path(temporary)
        observations_path = temporary_directory / "observations.ndjson"
        report_builder = MappingReportBuilder(
            table, processing_run_id, parser_version, MAPPER_VERSION
        )
        observation_count = 0
        with observations_path.open("wb") as observations:
            for result in results:
                report_builder.add(result)
                if result.observation is not None:
                    observations.write(serialize_observation(result.observation) + b"\n")
                    observation_count += 1
        report = report_builder.build()
        _write_json(temporary_directory / "mapping-report.json", report)
        (temporary_directory / "mapping-report.md").write_text(
            render_mapping_report(report), encoding="utf-8"
        )
        manifest = {
            "processingRunId": processing_run_id,
            "sourceSetId": table.source_set_id,
            "mappingVersion": table.mapping_version,
            "mapperVersion": MAPPER_VERSION,
            "parserVersion": parser_version,
            "observationCount": observation_count,
            "inputs": {
                "devicesArtifactId": table.devices_artifact_id,
                "rawArtifactId": table.raw_artifact_id,
                "mappingTableSha256": table.checksum,
            },
            "outputs": {
                name: _file_identity(temporary_directory / name)
                for name in (
                    "observations.ndjson",
                    "mapping-report.json",
                    "mapping-report.md",
                )
            },
        }
        _write_json(temporary_directory / "manifest.json", manifest)
        if final_directory.exists():
            _verify_same_run(temporary_directory, final_directory)
            status = "REUSED_VERIFIED"
        else:
            temporary_directory.replace(final_directory)
            status = "STORED"
    return CanonicalRunOutput(
        processing_run_id=processing_run_id,
        run_directory=final_directory,
        observation_count=observation_count,
        status=status,
        report=report,
    )


def write_review_report(report: dict[str, object], output_directory: Path) -> tuple[Path, Path]:
    output_directory.mkdir(parents=True, exist_ok=True)
    json_path = output_directory / "mapping-report.json"
    markdown_path = output_directory / "mapping-report.md"
    json_bytes = (json.dumps(report, indent=2, sort_keys=True) + "\n").encode()
    markdown_bytes = render_mapping_report(report).encode()
    _write_if_same_or_absent(json_path, json_bytes)
    _write_if_same_or_absent(markdown_path, markdown_bytes)
    return json_path, markdown_path


def _write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _file_identity(path: Path) -> dict[str, object]:
    content = path.read_bytes()
    return {"byteLength": len(content), "sha256": hashlib.sha256(content).hexdigest()}


def _verify_same_run(generated: Path, existing: Path) -> None:
    expected_names = {path.name for path in generated.iterdir()}
    actual_names = {path.name for path in existing.iterdir()}
    if expected_names != actual_names:
        raise ValueError("Existing Canonical Processing Run has different files")
    for name in expected_names:
        if _file_identity(generated / name) != _file_identity(existing / name):
            raise ValueError(f"Existing Canonical Processing Run differs: {name}")


def _write_if_same_or_absent(path: Path, content: bytes) -> None:
    if path.exists():
        if path.read_bytes() != content:
            raise ValueError(f"Review report already exists with different content: {path}")
        return
    path.write_bytes(content)
