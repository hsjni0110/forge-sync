from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

import pytest
from forgesync_evaluation.adapter.outbound.shdr_readings import (
    MalformedShdrLineError,
    read_shdr_readings,
)
from forgesync_evaluation.domain.naive_estimate import SourceReading


def test_reader_keeps_time_name_and_the_untouched_remainder_of_each_line(tmp_path: Path) -> None:
    payload = tmp_path / "payload"
    payload.write_bytes(
        b"2016-10-05T08:43:49.514Z|execution|STOPPED\n"
        b"2016-10-05T09:03:57.872Z|system_cond|Warning|406|||MEMORY PROTECT\n"
    )

    readings = read_shdr_readings(payload)

    assert readings == [
        SourceReading(datetime(2016, 10, 5, 8, 43, 49, 514000, tzinfo=UTC), "execution", "STOPPED"),
        SourceReading(
            datetime(2016, 10, 5, 9, 3, 57, 872000, tzinfo=UTC),
            "system_cond",
            "Warning|406|||MEMORY PROTECT",
        ),
    ]


def test_reader_rejects_a_line_without_a_value_and_names_its_line(tmp_path: Path) -> None:
    payload = tmp_path / "payload"
    payload.write_bytes(b"2016-10-05T08:43:49.514Z|execution|STOPPED\n2016-10-05T08:43:50Z|x\n")

    with pytest.raises(MalformedShdrLineError, match="line 2"):
        read_shdr_readings(payload)
