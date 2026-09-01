"""Use cases for acquiring and verifying source artifacts."""

from __future__ import annotations

from collections.abc import Callable, Iterator
from datetime import UTC, datetime
from typing import BinaryIO

from .domain import SourceArtifact, SourceLock
from .errors import AcquisitionError, ArtifactNotFoundError
from .ports import ArtifactStore, ReceiptStore, SourceReader

Clock = Callable[[], datetime]


def utc_now() -> datetime:
    return datetime.now(UTC)


class SourceAcquisitionService:
    def __init__(
        self,
        reader: SourceReader,
        artifact_store: ArtifactStore,
        receipt_store: ReceiptStore,
        clock: Clock = utc_now,
        chunk_size: int = 64 * 1024,
    ) -> None:
        self._reader = reader
        self._artifact_store = artifact_store
        self._receipt_store = receipt_store
        self._clock = clock
        self._chunk_size = chunk_size

    def acquire(self, source_lock: SourceLock) -> tuple[SourceArtifact, ...]:
        acquired: list[SourceArtifact] = []
        started_at = self._clock().isoformat()
        try:
            for spec in source_lock.artifacts:
                try:
                    acquired.append(self._artifact_store.verify(spec))
                    continue
                except ArtifactNotFoundError:
                    pass
                with self._reader.open(spec.source_uri) as source:
                    artifact = self._artifact_store.preserve(
                        spec=spec,
                        source_lock=source_lock,
                        chunks=self._read_chunks(source),
                        retrieved_at=self._clock().isoformat(),
                    )
                    acquired.append(artifact)
        except (OSError, AcquisitionError) as error:
            self._record_receipt(source_lock, started_at, "FAILED", acquired, str(error))
            if isinstance(error, AcquisitionError):
                raise
            raise AcquisitionError(str(error)) from error
        self._record_receipt(source_lock, started_at, "SUCCEEDED", acquired, None)
        return tuple(acquired)

    def verify(self, source_lock: SourceLock) -> tuple[SourceArtifact, ...]:
        return tuple(self._artifact_store.verify(spec) for spec in source_lock.artifacts)

    def _read_chunks(self, source: BinaryIO) -> Iterator[bytes]:
        while chunk := source.read(self._chunk_size):
            yield chunk

    def _record_receipt(
        self,
        source_lock: SourceLock,
        started_at: str,
        status: str,
        artifacts: list[SourceArtifact],
        error: str | None,
    ) -> None:
        self._receipt_store.record(
            {
                "sourceSetId": source_lock.source_set_id,
                "startedAt": started_at,
                "finishedAt": self._clock().isoformat(),
                "status": status,
                "artifacts": [artifact.artifact_id for artifact in artifacts],
                "error": error,
            }
        )
