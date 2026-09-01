from __future__ import annotations

import hashlib
from pathlib import Path

import pytest
from forgesync_edge.source_acquisition.domain import (
    ArtifactRole,
    SourceArtifactSpec,
    SourceLock,
)

FIXTURES = Path(__file__).parent / "fixtures"


def artifact_spec(alias: str, role: ArtifactRole, content: bytes, path: str) -> SourceArtifactSpec:
    digest = hashlib.sha256(content).hexdigest()
    return SourceArtifactSpec(
        alias=alias,
        role=role,
        repository_path=path,
        source_uri=f"https://example.test/pinned-commit/{path}",
        media_type="application/xml" if role is ArtifactRole.MTCONNECT_DEVICES else "text/plain",
        expected_byte_length=len(content),
        sha256=digest,
    )


def source_lock(*specs: SourceArtifactSpec) -> SourceLock:
    return SourceLock(
        source_set_id="nist-mazak01-20161005",
        source_name="NIST test fixture",
        upstream_repository="https://example.test/repository",
        upstream_commit="pinned-commit",
        license_or_terms_reference="https://example.test/terms",
        evidence_state="DERIVED_FIXTURE",
        artifacts=tuple(specs),
    )


@pytest.fixture
def devices_bytes() -> bytes:
    return (FIXTURES / "Devices-minimal.xml").read_bytes()


@pytest.fixture
def representative_raw_bytes() -> bytes:
    return (
        b"2016-10-05T08:43:49.514Z|Srpm|0\n"
        b"2016-10-05T08:43:49.514Z|execution|STOPPED\n"
        b"2016-10-05T08:43:49.514Z|servo_cond|Normal||||\n"
        b"2016-10-05T08:43:50.000Z|future_item|10\n"
        b"not-a-record\n"
    )
