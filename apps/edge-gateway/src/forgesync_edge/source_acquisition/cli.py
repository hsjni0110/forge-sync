"""Command-line interface for immutable source acquisition and profiling."""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from pathlib import Path
from typing import Any

from .application import SourceAcquisitionService
from .domain import ArtifactRole, SourceArtifact
from .errors import AcquisitionError, ConfigurationError, ProfileError
from .filesystem_store import FilesystemArtifactStore, FilesystemReceiptStore
from .http_reader import HttpsSourceReader
from .profile import SourceProfileGenerator, write_profile
from .shdr_decoder import RawRecordDecoder
from .source_lock import load_source_lock
from .xml_catalog import read_machine_catalog

EXIT_CONFIGURATION = 2
EXIT_ACQUISITION = 3
EXIT_PROFILE = 4


def main(arguments: Sequence[str] | None = None) -> int:
    parser = _parser()
    namespace = parser.parse_args(arguments)
    try:
        result = _execute(namespace)
    except ConfigurationError as error:
        return _report_error(EXIT_CONFIGURATION, "CONFIGURATION_ERROR", error)
    except AcquisitionError as error:
        return _report_error(EXIT_ACQUISITION, "ACQUISITION_ERROR", error)
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


def _report_error(exit_code: int, error_code: str, error: Exception) -> int:
    print(
        json.dumps({"error": error_code, "message": str(error)}, sort_keys=True),
        file=sys.stderr,
    )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
