"""Anti-corruption adapter from Source Acquisition models to Ingestion candidates."""

from __future__ import annotations

from collections.abc import Iterable, Iterator

from ....source_acquisition.domain import DataItemCatalogEntry, MachineCatalog, RawRecord
from ...domain.mapping import CatalogDataItem, CatalogSnapshot, MappingCandidate


def adapt_catalog(catalog: MachineCatalog) -> CatalogSnapshot:
    return CatalogSnapshot(
        machine_id=catalog.machine_id,
        data_items=tuple(_adapt_data_item(entry) for entry in catalog.entries),
    )


def adapt_records(
    records: Iterable[RawRecord], catalog: CatalogSnapshot
) -> Iterator[MappingCandidate]:
    entries_by_name = catalog.by_name()
    for record in records:
        matches = entries_by_name.get(record.data_item_name_raw or "", ())
        data_item = matches[0] if len(matches) == 1 else None
        yield MappingCandidate(
            artifact_id=record.artifact_id,
            raw_record_id=record.raw_payload_ref,
            source_event_key=record.raw_payload_ref,
            line_number=record.line_number,
            timestamp_raw=record.timestamp_raw,
            value_fields_raw=record.value_fields_raw,
            parse_status=record.parse_status.value,
            parse_error=record.parse_error,
            data_item=data_item,
            data_item_name_raw=record.data_item_name_raw,
        )


def _adapt_data_item(entry: DataItemCatalogEntry) -> CatalogDataItem:
    return CatalogDataItem(
        data_item_id=entry.data_item_id,
        component_id=entry.component_id,
        name=entry.name,
        category=entry.category,
        type=entry.type,
        subtype=entry.subtype,
        unit=entry.units,
    )
