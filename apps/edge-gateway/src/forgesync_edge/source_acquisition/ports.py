"""Ports owned by the source acquisition application layer."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import AbstractContextManager
from pathlib import Path
from typing import BinaryIO, Protocol

from .domain import SourceArtifact, SourceArtifactSpec, SourceLock


class SourceReader(Protocol):
    def open(self, source_uri: str) -> AbstractContextManager[BinaryIO]: ...


class ArtifactStore(Protocol):
    def preserve(
        self,
        spec: SourceArtifactSpec,
        source_lock: SourceLock,
        chunks: Iterator[bytes],
        retrieved_at: str,
    ) -> SourceArtifact: ...

    def verify(self, spec: SourceArtifactSpec) -> SourceArtifact: ...

    def payload_path(self, spec: SourceArtifactSpec) -> Path: ...


class ReceiptStore(Protocol):
    def record(self, receipt: dict[str, object]) -> Path: ...
