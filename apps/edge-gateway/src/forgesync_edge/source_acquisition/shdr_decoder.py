"""Lossless line decoder for the selected NIST SHDR-like raw records."""

from __future__ import annotations

import hashlib
from collections.abc import Iterator
from datetime import datetime
from pathlib import Path

from .domain import MachineCatalog, ParseStatus, RawRecord

PARSER_VERSION = "1.0.0"


class RawRecordDecoder:
    def __init__(self, catalog: MachineCatalog) -> None:
        self._entries_by_name = catalog.entries_by_name()

    def decode(self, payload_path: Path, artifact_id: str) -> Iterator[RawRecord]:
        byte_offset = 0
        with payload_path.open("rb") as payload:
            for line_number, raw_line in enumerate(payload, start=1):
                byte_start = byte_offset
                byte_offset += len(raw_line)
                yield self._decode_line(
                    raw_line=raw_line,
                    artifact_id=artifact_id,
                    line_number=line_number,
                    byte_start=byte_start,
                    byte_end=byte_offset,
                )

    def _decode_line(
        self,
        raw_line: bytes,
        artifact_id: str,
        line_number: int,
        byte_start: int,
        byte_end: int,
    ) -> RawRecord:
        locator = f"{artifact_id}#bytes={byte_start}-{max(byte_start, byte_end - 1)}"
        line_hash = hashlib.sha256(raw_line).hexdigest()
        try:
            decoded = raw_line.decode("utf-8")
        except UnicodeDecodeError as error:
            return self._invalid(
                artifact_id,
                line_number,
                byte_start,
                byte_end,
                locator,
                line_hash,
                f"INVALID_UTF8:{error.start}",
            )

        content = decoded.removesuffix("\n").removesuffix("\r")
        timestamp_raw, first_separator, remainder = content.partition("|")
        name_raw, second_separator, payload_raw = remainder.partition("|")
        if not first_separator or not second_separator or not timestamp_raw or not name_raw:
            return self._invalid(
                artifact_id,
                line_number,
                byte_start,
                byte_end,
                locator,
                line_hash,
                "MALFORMED_FIELDS",
                timestamp_raw or None,
                name_raw or None,
            )
        if not _is_timezone_aware_iso_timestamp(timestamp_raw):
            return self._invalid(
                artifact_id,
                line_number,
                byte_start,
                byte_end,
                locator,
                line_hash,
                "INVALID_TIMESTAMP",
                timestamp_raw,
                name_raw,
                (payload_raw,),
            )

        matching_entries = self._entries_by_name.get(name_raw, ())
        if not matching_entries:
            return self._record(
                artifact_id,
                line_number,
                byte_start,
                byte_end,
                locator,
                line_hash,
                timestamp_raw,
                name_raw,
                (payload_raw,),
                ParseStatus.UNKNOWN_DATA_ITEM,
                "DATA_ITEM_NOT_IN_MACHINE_CATALOG",
                None,
            )
        if len(matching_entries) > 1:
            return self._record(
                artifact_id,
                line_number,
                byte_start,
                byte_end,
                locator,
                line_hash,
                timestamp_raw,
                name_raw,
                (payload_raw,),
                ParseStatus.AMBIGUOUS_DATA_ITEM,
                "DATA_ITEM_NAME_IS_AMBIGUOUS",
                None,
            )

        category = matching_entries[0].category
        value_fields: tuple[str, ...] = (payload_raw,)
        if category == "CONDITION":
            value_fields = tuple(payload_raw.split("|", maxsplit=4))
            if len(value_fields) != 5:
                return self._invalid(
                    artifact_id,
                    line_number,
                    byte_start,
                    byte_end,
                    locator,
                    line_hash,
                    "CONDITION_FIELD_COUNT",
                    timestamp_raw,
                    name_raw,
                    value_fields,
                    category,
                )
        return self._record(
            artifact_id,
            line_number,
            byte_start,
            byte_end,
            locator,
            line_hash,
            timestamp_raw,
            name_raw,
            value_fields,
            ParseStatus.PARSED,
            None,
            category,
        )

    @staticmethod
    def _invalid(
        artifact_id: str,
        line_number: int,
        byte_start: int,
        byte_end: int,
        locator: str,
        line_hash: str,
        error: str,
        timestamp: str | None = None,
        name: str | None = None,
        fields: tuple[str, ...] = (),
        category: str | None = None,
    ) -> RawRecord:
        return RawRecordDecoder._record(
            artifact_id,
            line_number,
            byte_start,
            byte_end,
            locator,
            line_hash,
            timestamp,
            name,
            fields,
            ParseStatus.INVALID,
            error,
            category,
        )

    @staticmethod
    def _record(
        artifact_id: str,
        line_number: int,
        byte_start: int,
        byte_end: int,
        locator: str,
        line_hash: str,
        timestamp: str | None,
        name: str | None,
        fields: tuple[str, ...],
        status: ParseStatus,
        error: str | None,
        category: str | None,
    ) -> RawRecord:
        return RawRecord(
            artifact_id=artifact_id,
            line_number=line_number,
            byte_start=byte_start,
            byte_end=byte_end,
            raw_payload_ref=locator,
            raw_line_sha256=line_hash,
            timestamp_raw=timestamp,
            data_item_name_raw=name,
            value_fields_raw=fields,
            parse_status=status,
            parse_error=error,
            parser_version=PARSER_VERSION,
            category=category,
        )


def parse_source_timestamp(value: str) -> datetime:
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    return datetime.fromisoformat(normalized)


def _is_timezone_aware_iso_timestamp(value: str) -> bool:
    try:
        parsed = parse_source_timestamp(value)
    except ValueError:
        return False
    return parsed.tzinfo is not None
