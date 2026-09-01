"""Deterministic Canonical Observation identity adapter."""

from __future__ import annotations

from uuid import NAMESPACE_URL, UUID, uuid5


class Uuid5ObservationIdGenerator:
    def generate(self, mapping_version: str, raw_record_id: str) -> UUID:
        identity = (
            f"https://forgesync.local/canonical-observation/{mapping_version}/{raw_record_id}"
        )
        return uuid5(NAMESPACE_URL, identity)
