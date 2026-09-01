"""Consumer-owned ports for deterministic Canonical Observation mapping."""

from __future__ import annotations

from typing import Protocol
from uuid import UUID


class ObservationIdGenerator(Protocol):
    def generate(self, mapping_version: str, raw_record_id: str) -> UUID: ...
