"""Load and validate pinned external source declarations."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from .domain import ArtifactRole, SourceArtifactSpec, SourceLock
from .errors import ConfigurationError

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def load_source_lock(path: Path) -> SourceLock:
    try:
        document: Any = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ConfigurationError(f"Cannot read source lock {path}: {error}") from error

    if not isinstance(document, dict):
        raise ConfigurationError("Source lock root must be an object")

    try:
        artifacts_document = document["artifacts"]
        if not isinstance(artifacts_document, list):
            raise ConfigurationError("artifacts must be an array")
        artifacts = tuple(_parse_artifact(item) for item in artifacts_document)
        source_lock = SourceLock(
            source_set_id=_required_string(document, "sourceSetId"),
            source_name=_required_string(document, "sourceName"),
            upstream_repository=_required_string(document, "upstreamRepository"),
            upstream_commit=_required_string(document, "upstreamCommit"),
            license_or_terms_reference=_required_string(document, "licenseOrTermsReference"),
            evidence_state=_required_string(document, "evidenceState"),
            artifacts=artifacts,
        )
    except (KeyError, TypeError, ValueError) as error:
        if isinstance(error, ConfigurationError):
            raise
        raise ConfigurationError(f"Invalid source lock: {error}") from error

    _validate_source_lock(source_lock)
    return source_lock


def _parse_artifact(document: object) -> SourceArtifactSpec:
    if not isinstance(document, dict):
        raise ConfigurationError("Each artifact must be an object")
    length = document.get("expectedByteLength")
    if not isinstance(length, int) or isinstance(length, bool) or length <= 0:
        raise ConfigurationError("expectedByteLength must be a positive integer")
    try:
        role = ArtifactRole(_required_string(document, "role"))
    except ValueError as error:
        raise ConfigurationError(f"Unsupported artifact role: {document.get('role')}") from error
    return SourceArtifactSpec(
        alias=_required_string(document, "alias"),
        role=role,
        repository_path=_required_string(document, "repositoryPath"),
        source_uri=_required_string(document, "sourceUri"),
        media_type=_required_string(document, "mediaType"),
        expected_byte_length=length,
        sha256=_required_string(document, "sha256").lower(),
    )


def _required_string(document: dict[str, Any], key: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ConfigurationError(f"{key} must be a non-empty string")
    return value


def _validate_source_lock(source_lock: SourceLock) -> None:
    if not source_lock.artifacts:
        raise ConfigurationError("Source lock must declare at least one artifact")
    aliases = [artifact.alias for artifact in source_lock.artifacts]
    if len(aliases) != len(set(aliases)):
        raise ConfigurationError("Artifact aliases must be unique")
    for artifact in source_lock.artifacts:
        if not SHA256_PATTERN.fullmatch(artifact.sha256):
            raise ConfigurationError(f"Invalid SHA-256 for {artifact.alias}")
        parsed = urlparse(artifact.source_uri)
        if parsed.scheme != "https":
            raise ConfigurationError(f"Production source URI must use HTTPS: {artifact.alias}")
        if parsed.username or parsed.password or parsed.fragment:
            raise ConfigurationError(
                f"Source URI contains forbidden URI components: {artifact.alias}"
            )
        if source_lock.upstream_commit not in artifact.source_uri:
            raise ConfigurationError(
                f"Source URI is not pinned to upstream commit: {artifact.alias}"
            )
