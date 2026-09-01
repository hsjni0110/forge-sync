"""Deterministic Processing Run identity for Canonical mapping."""

from __future__ import annotations

import hashlib
import json

from ..domain.mapping import MappingTable

MAPPER_VERSION = "2.0.0"


def canonical_processing_run_id(table: MappingTable, parser_version: str) -> str:
    identity = {
        "devicesArtifactId": table.devices_artifact_id,
        "machineId": table.machine_id,
        "mapperVersion": MAPPER_VERSION,
        "mappingTableSha256": table.checksum,
        "mappingVersion": table.mapping_version,
        "parserVersion": parser_version,
        "rawArtifactId": table.raw_artifact_id,
        "sourceSetId": table.source_set_id,
    }
    encoded = json.dumps(identity, separators=(",", ":"), sort_keys=True).encode()
    return f"sha256:{hashlib.sha256(encoded).hexdigest()}"
