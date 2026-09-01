from __future__ import annotations

from datetime import UTC, datetime
from io import BytesIO
from pathlib import Path

import pytest
from conftest import artifact_spec, source_lock
from forgesync_edge.source_acquisition.application import SourceAcquisitionService
from forgesync_edge.source_acquisition.domain import AcquisitionStatus, ArtifactRole
from forgesync_edge.source_acquisition.errors import IntegrityError
from forgesync_edge.source_acquisition.filesystem_store import (
    FilesystemArtifactStore,
    FilesystemReceiptStore,
)


class MemoryReader:
    def __init__(self, content_by_uri: dict[str, bytes]) -> None:
        self._content_by_uri = content_by_uri
        self.open_count = 0

    def open(self, source_uri: str) -> BytesIO:
        self.open_count += 1
        return BytesIO(self._content_by_uri[source_uri])


def fixed_clock() -> datetime:
    return datetime(2026, 9, 1, tzinfo=UTC)


def test_preserves_exact_bytes_and_reuses_verified_content(tmp_path: Path) -> None:
    content = b"raw\r\nbytes\x00"
    spec = artifact_spec("raw", ArtifactRole.SHDR_RAW, content, "raw.txt")
    lock = source_lock(spec)
    store = FilesystemArtifactStore(tmp_path)
    reader = MemoryReader({spec.source_uri: content})
    service = SourceAcquisitionService(
        reader,
        store,
        FilesystemReceiptStore(tmp_path),
        clock=fixed_clock,
        chunk_size=3,
    )

    first = service.acquire(lock)[0]
    second = service.acquire(lock)[0]

    assert first.payload_path.read_bytes() == content
    assert first.acquisition_status is AcquisitionStatus.STORED
    assert second.acquisition_status is AcquisitionStatus.REUSED_VERIFIED
    assert first.artifact_id == second.artifact_id
    assert reader.open_count == 1


def test_does_not_register_partial_or_wrong_content(tmp_path: Path) -> None:
    expected = b"complete content"
    partial = b"complete"
    spec = artifact_spec("raw", ArtifactRole.SHDR_RAW, expected, "raw.txt")
    store = FilesystemArtifactStore(tmp_path)

    with pytest.raises(IntegrityError, match="Byte length mismatch"):
        store.preserve(spec, source_lock(spec), iter((partial,)), fixed_clock().isoformat())

    assert not store.payload_path(spec).exists()
    assert not (store.payload_path(spec).parent / "manifest.json").exists()


def test_refuses_to_overwrite_corrupted_existing_artifact(tmp_path: Path) -> None:
    content = b"trusted"
    spec = artifact_spec("raw", ArtifactRole.SHDR_RAW, content, "raw.txt")
    store = FilesystemArtifactStore(tmp_path)
    stored = store.preserve(spec, source_lock(spec), iter((content,)), fixed_clock().isoformat())
    stored.payload_path.write_bytes(b"corrupt")

    with pytest.raises(IntegrityError):
        store.preserve(spec, source_lock(spec), iter((content,)), fixed_clock().isoformat())

    assert stored.payload_path.read_bytes() == b"corrupt"
