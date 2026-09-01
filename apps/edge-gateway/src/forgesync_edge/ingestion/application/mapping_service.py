"""Application orchestration for streaming source candidates through semantic mapping."""

from __future__ import annotations

from collections.abc import Iterable, Iterator

from ..domain.mapping import (
    CatalogSnapshot,
    MappingCandidate,
    MappingContext,
    MappingResult,
    MappingTable,
    ObservationMapper,
)
from .ports import ObservationIdGenerator


class MapCanonicalObservations:
    def __init__(
        self,
        mapping_table: MappingTable,
        catalog: CatalogSnapshot,
        id_generator: ObservationIdGenerator,
        mapper: ObservationMapper | None = None,
    ) -> None:
        mapping_table.validate_catalog(catalog)
        self._mapping_table = mapping_table
        self._definitions = mapping_table.by_data_item_id()
        self._id_generator = id_generator
        self._mapper = mapper or ObservationMapper()
        self._context = MappingContext(
            source_set_id=mapping_table.source_set_id,
            machine_id=mapping_table.machine_id,
            mapping_version=mapping_table.mapping_version,
        )

    def map(self, candidates: Iterable[MappingCandidate]) -> Iterator[MappingResult]:
        for candidate in candidates:
            if candidate.artifact_id != self._mapping_table.raw_artifact_id:
                raise ValueError("Mapping candidate artifact differs from mapping table")
            definition = (
                self._definitions.get(candidate.data_item.data_item_id)
                if candidate.data_item is not None
                else None
            )
            event_id = (
                self._id_generator.generate(
                    self._mapping_table.mapping_version,
                    candidate.raw_record_id,
                )
                if definition is not None and candidate.parse_status == "PARSED"
                else None
            )
            yield self._mapper.map(candidate, definition, event_id, self._context)
