"""Content-addressed immutable filesystem adapters."""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
import uuid
from collections.abc import Iterator
from pathlib import Path
from typing import Any

from .domain import AcquisitionStatus, SourceArtifact, SourceArtifactSpec, SourceLock
from .errors import AcquisitionError, ArtifactNotFoundError, IntegrityError

COLLECTOR_VERSION = "0.1.0"


class FilesystemArtifactStore:
    def __init__(self, root: Path) -> None:
        self._root = root

    def preserve(
        self,
        spec: SourceArtifactSpec,
        source_lock: SourceLock,
        chunks: Iterator[bytes],
        retrieved_at: str,
    ) -> SourceArtifact:
        existing = self._artifact_directory(spec)
        if existing.exists():
            return self._verified_artifact(spec, AcquisitionStatus.REUSED_VERIFIED)

        temporary_root = self._root / ".tmp"
        temporary_root.mkdir(parents=True, exist_ok=True)
        temporary_directory = Path(tempfile.mkdtemp(prefix="acquire-", dir=temporary_root))
        temporary_payload = temporary_directory / "payload"
        try:
            actual_length, actual_hash = self._write_and_hash(temporary_payload, chunks)
            self._assert_expected_identity(spec, actual_length, actual_hash)
            manifest = self._manifest(spec, source_lock, retrieved_at)
            self._write_json(temporary_directory / "manifest.json", manifest)
            existing.parent.mkdir(parents=True, exist_ok=True)
            try:
                os.rename(temporary_directory, existing)
            except FileExistsError:
                shutil.rmtree(temporary_directory)
                return self._verified_artifact(spec, AcquisitionStatus.REUSED_VERIFIED)
            return self._verified_artifact(spec, AcquisitionStatus.STORED)
        except Exception:
            if temporary_directory.exists():
                shutil.rmtree(temporary_directory)
            raise

    def verify(self, spec: SourceArtifactSpec) -> SourceArtifact:
        return self._verified_artifact(spec, AcquisitionStatus.REUSED_VERIFIED)

    def payload_path(self, spec: SourceArtifactSpec) -> Path:
        return self._artifact_directory(spec) / "payload"

    def _verified_artifact(
        self, spec: SourceArtifactSpec, status: AcquisitionStatus
    ) -> SourceArtifact:
        artifact_directory = self._artifact_directory(spec)
        payload_path = artifact_directory / "payload"
        manifest_path = artifact_directory / "manifest.json"
        if not artifact_directory.exists():
            raise ArtifactNotFoundError(f"Artifact is not present: {spec.artifact_id}")
        if not payload_path.is_file() or not manifest_path.is_file():
            raise IntegrityError(f"Artifact is incomplete: {spec.artifact_id}")
        actual_length, actual_hash = self._hash_file(payload_path)
        self._assert_expected_identity(spec, actual_length, actual_hash)
        try:
            manifest: Any = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise IntegrityError(f"Artifact manifest is invalid: {spec.artifact_id}") from error
        if not isinstance(manifest, dict):
            raise IntegrityError(f"Artifact manifest is not an object: {spec.artifact_id}")
        if manifest.get("artifactId") != spec.artifact_id:
            raise IntegrityError(f"Artifact manifest identity mismatch: {spec.artifact_id}")
        return SourceArtifact(
            artifact_id=spec.artifact_id,
            alias=spec.alias,
            payload_path=payload_path,
            manifest_path=manifest_path,
            byte_length=actual_length,
            sha256=actual_hash,
            acquisition_status=status,
        )

    def _artifact_directory(self, spec: SourceArtifactSpec) -> Path:
        return self._root / "nist" / "sha256" / spec.sha256

    @staticmethod
    def _write_and_hash(path: Path, chunks: Iterator[bytes]) -> tuple[int, str]:
        digest = hashlib.sha256()
        byte_length = 0
        try:
            with path.open("xb") as output:
                for chunk in chunks:
                    if not isinstance(chunk, bytes):
                        raise AcquisitionError("Source reader yielded a non-byte chunk")
                    if not chunk:
                        continue
                    output.write(chunk)
                    digest.update(chunk)
                    byte_length += len(chunk)
                output.flush()
                os.fsync(output.fileno())
        except OSError as error:
            raise AcquisitionError(f"Cannot persist source artifact: {error}") from error
        return byte_length, digest.hexdigest()

    @staticmethod
    def _hash_file(path: Path) -> tuple[int, str]:
        digest = hashlib.sha256()
        byte_length = 0
        try:
            with path.open("rb") as source:
                while chunk := source.read(64 * 1024):
                    digest.update(chunk)
                    byte_length += len(chunk)
        except OSError as error:
            raise IntegrityError(f"Cannot read artifact payload: {error}") from error
        return byte_length, digest.hexdigest()

    @staticmethod
    def _assert_expected_identity(
        spec: SourceArtifactSpec, actual_length: int, actual_hash: str
    ) -> None:
        if actual_length != spec.expected_byte_length:
            raise IntegrityError(
                f"Byte length mismatch for {spec.alias}: "
                f"expected {spec.expected_byte_length}, got {actual_length}"
            )
        if actual_hash != spec.sha256:
            raise IntegrityError(
                f"SHA-256 mismatch for {spec.alias}: expected {spec.sha256}, got {actual_hash}"
            )

    @staticmethod
    def _manifest(
        spec: SourceArtifactSpec, source_lock: SourceLock, retrieved_at: str
    ) -> dict[str, object]:
        return {
            "artifactId": spec.artifact_id,
            "sourceName": source_lock.source_name,
            "sourceAlias": spec.alias,
            "sourceRole": spec.role.value,
            "sourceUri": spec.source_uri,
            "repositoryPath": spec.repository_path,
            "upstreamRepository": source_lock.upstream_repository,
            "upstreamCommit": source_lock.upstream_commit,
            "retrievedAt": retrieved_at,
            "mediaType": spec.media_type,
            "byteLength": spec.expected_byte_length,
            "sha256": spec.sha256,
            "licenseOrTermsReference": source_lock.license_or_terms_reference,
            "evidenceState": source_lock.evidence_state,
            "collectorVersion": COLLECTOR_VERSION,
        }

    @staticmethod
    def _write_json(path: Path, document: dict[str, object]) -> None:
        path.write_text(
            json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )


class FilesystemReceiptStore:
    def __init__(self, root: Path) -> None:
        self._receipts_directory = root / "nist" / "receipts"

    def record(self, receipt: dict[str, object]) -> Path:
        self._receipts_directory.mkdir(parents=True, exist_ok=True)
        receipt_path = self._receipts_directory / f"{uuid.uuid4().hex}.json"
        receipt_path.write_text(
            json.dumps(receipt, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return receipt_path
