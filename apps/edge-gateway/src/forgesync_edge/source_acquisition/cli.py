"""Command-line interface for immutable source acquisition and profiling."""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from pathlib import Path
from typing import Any

from forgesync_edge.ingestion.adapter.inbound import (
    adapt_catalog,
    adapt_records,
    load_mapping_table,
)
from forgesync_edge.ingestion.adapter.outbound import (
    Uuid5ObservationIdGenerator,
    write_canonical_run,
    write_review_report,
)
from forgesync_edge.ingestion.application import MapCanonicalObservations
from forgesync_edge.ingestion.application.errors import MappingError
from forgesync_edge.ingestion.application.run_identity import canonical_processing_run_id
from forgesync_edge.ingestion.domain import MappingTable

from .application import SourceAcquisitionService
from .domain import ArtifactRole, SourceArtifact, SourceArtifactSpec, SourceLock
from .errors import AcquisitionError, ConfigurationError, ProfileError
from .filesystem_store import FilesystemArtifactStore, FilesystemReceiptStore
from .http_reader import HttpsSourceReader
from .profile import SourceProfileGenerator, write_profile
from .shdr_decoder import PARSER_VERSION, RawRecordDecoder
from .source_lock import load_source_lock
from .xml_catalog import read_machine_catalog

EXIT_CONFIGURATION = 2
EXIT_ACQUISITION = 3
EXIT_PROFILE = 4
EXIT_MAPPING = 5


def main(arguments: Sequence[str] | None = None) -> int:
    parser = _parser()
    namespace = parser.parse_args(arguments)
    try:
        result = _execute(namespace)
    except ConfigurationError as error:
        return _report_error(EXIT_CONFIGURATION, "CONFIGURATION_ERROR", error)
    except AcquisitionError as error:
        return _report_error(EXIT_ACQUISITION, "ACQUISITION_ERROR", error)
    except MappingError as error:
        return _report_error(EXIT_MAPPING, "MAPPING_ERROR", error)
    except (ProfileError, ValueError) as error:
        return _report_error(EXIT_PROFILE, "PROFILE_ERROR", error)
    print(json.dumps(result, sort_keys=True, ensure_ascii=False))
    return 0


def _execute(namespace: argparse.Namespace) -> dict[str, Any]:
    source_lock = load_source_lock(namespace.lock)
    artifact_store = FilesystemArtifactStore(namespace.store)
    service = SourceAcquisitionService(
        reader=HttpsSourceReader(),
        artifact_store=artifact_store,
        receipt_store=FilesystemReceiptStore(namespace.store),
    )
    if namespace.command == "acquire":
        artifacts = service.acquire(source_lock)
        return {
            "command": "acquire",
            "sourceSetId": source_lock.source_set_id,
            "artifacts": [_artifact_result(artifact) for artifact in artifacts],
        }
    if namespace.command == "verify":
        artifacts = service.verify(source_lock)
        return {
            "command": "verify",
            "sourceSetId": source_lock.source_set_id,
            "artifacts": [_artifact_result(artifact) for artifact in artifacts],
        }
    if namespace.command == "profile":
        service.verify(source_lock)
        devices_spec = source_lock.artifact_for_role(ArtifactRole.MTCONNECT_DEVICES)
        raw_spec = source_lock.artifact_for_role(ArtifactRole.SHDR_RAW)
        catalog = read_machine_catalog(artifact_store.payload_path(devices_spec), namespace.machine)
        records = RawRecordDecoder(catalog).decode(
            artifact_store.payload_path(raw_spec), raw_spec.artifact_id
        )
        profile = SourceProfileGenerator().generate(
            source_lock=source_lock,
            catalog=catalog,
            records=records,
            devices_spec=devices_spec,
            raw_spec=raw_spec,
        )
        json_path, markdown_path = write_profile(profile, namespace.output)
        return {
            "command": "profile",
            "sourceSetId": source_lock.source_set_id,
            "processingRunId": profile["processingRunId"],
            "profileJson": str(json_path),
            "profileMarkdown": str(markdown_path),
        }
    if namespace.command == "map-observations":
        return _map_observations(namespace, source_lock, artifact_store, service)
    raise ConfigurationError(f"Unknown command: {namespace.command}")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forgesync-source")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("acquire", "verify"):
        command_parser = subparsers.add_parser(command)
        _common_arguments(command_parser)
    profile_parser = subparsers.add_parser("profile")
    _common_arguments(profile_parser)
    profile_parser.add_argument("--machine", required=True)
    profile_parser.add_argument("--output", required=True, type=Path)
    mapping_parser = subparsers.add_parser("map-observations")
    _common_arguments(mapping_parser)
    mapping_parser.add_argument("--machine", required=True)
    mapping_parser.add_argument("--mapping", required=True, type=Path)
    mapping_parser.add_argument("--canonical-store", required=True, type=Path)
    mapping_parser.add_argument("--report-output", required=True, type=Path)
    return parser


def _common_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--lock", required=True, type=Path)
    parser.add_argument("--store", required=True, type=Path)


def _artifact_result(artifact: SourceArtifact) -> dict[str, object]:
    return {
        "alias": artifact.alias,
        "artifactId": artifact.artifact_id,
        "byteLength": artifact.byte_length,
        "sha256": artifact.sha256,
        "status": artifact.acquisition_status.value,
    }


def _map_observations(
    namespace: argparse.Namespace,
    source_lock: SourceLock,
    artifact_store: FilesystemArtifactStore,
    service: SourceAcquisitionService,
) -> dict[str, Any]:
    try:
        service.verify(source_lock)
        devices_spec = source_lock.artifact_for_role(ArtifactRole.MTCONNECT_DEVICES)
        raw_spec = source_lock.artifact_for_role(ArtifactRole.SHDR_RAW)
        table = load_mapping_table(namespace.mapping)
        _validate_mapping_source(table, source_lock, devices_spec, raw_spec, namespace.machine)
        source_catalog = read_machine_catalog(
            artifact_store.payload_path(devices_spec), namespace.machine
        )
        catalog = adapt_catalog(source_catalog)
        use_case = MapCanonicalObservations(
            mapping_table=table,
            catalog=catalog,
            id_generator=Uuid5ObservationIdGenerator(),
        )
        records = RawRecordDecoder(source_catalog).decode(
            artifact_store.payload_path(raw_spec), raw_spec.artifact_id
        )
        processing_run_id = canonical_processing_run_id(table, PARSER_VERSION)
        output = write_canonical_run(
            results=use_case.map(adapt_records(records, catalog)),
            table=table,
            processing_run_id=processing_run_id,
            parser_version=PARSER_VERSION,
            canonical_store=namespace.canonical_store,
        )
        report_json, report_markdown = write_review_report(output.report, namespace.report_output)
    except (OSError, ValueError) as error:
        raise MappingError(str(error)) from error
    return {
        "command": "map-observations",
        "sourceSetId": table.source_set_id,
        "processingRunId": processing_run_id,
        "status": output.status,
        "observationCount": output.observation_count,
        "runDirectory": str(output.run_directory),
        "reportJson": str(report_json),
        "reportMarkdown": str(report_markdown),
    }


def _validate_mapping_source(
    table: MappingTable,
    source_lock: SourceLock,
    devices_spec: SourceArtifactSpec,
    raw_spec: SourceArtifactSpec,
    machine_id: str,
) -> None:
    expected = (
        table.source_set_id,
        table.machine_id,
        table.devices_artifact_id,
        table.raw_artifact_id,
    )
    actual = (
        source_lock.source_set_id,
        machine_id,
        devices_spec.artifact_id,
        raw_spec.artifact_id,
    )
    if expected != actual:
        raise ValueError("Mapping table source identities do not match the source lock")


def _report_error(exit_code: int, error_code: str, error: Exception) -> int:
    print(
        json.dumps({"error": error_code, "message": str(error)}, sort_keys=True),
        file=sys.stderr,
    )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
