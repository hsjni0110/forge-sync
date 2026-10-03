"""Read SHDR lines the way a naive dashboard would: split, never interpret."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from forgesync_evaluation.domain.naive_estimate import SourceReading


class MalformedShdrLineError(ValueError):
    pass


def read_shdr_readings(payload_path: Path) -> list[SourceReading]:
    with payload_path.open(encoding="utf-8") as payload:
        return [_reading(line, line_number) for line_number, line in enumerate(payload, start=1)]


def _reading(line: str, line_number: int) -> SourceReading:
    timestamp, _, remainder = line.rstrip("\r\n").partition("|")
    data_item_name, separator, value = remainder.partition("|")
    if not separator or not data_item_name:
        raise MalformedShdrLineError(f"SHDR line {line_number} has no data item value")
    return SourceReading(datetime.fromisoformat(timestamp), data_item_name, value)
