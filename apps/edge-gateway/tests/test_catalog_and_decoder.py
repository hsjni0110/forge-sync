from __future__ import annotations

import hashlib
from pathlib import Path

from conftest import FIXTURES
from forgesync_edge.source_acquisition.domain import ParseStatus
from forgesync_edge.source_acquisition.shdr_decoder import RawRecordDecoder
from forgesync_edge.source_acquisition.xml_catalog import read_machine_catalog


def test_golden_fixture_preserves_declared_bytes() -> None:
    fixture = FIXTURES / "mazak01-20161005-golden.shdr"

    assert len(fixture.read_bytes()) == 586
    assert hashlib.sha256(fixture.read_bytes()).hexdigest() == (
        "3a4f044590eb3bc28deaf600c24eaa1a7a4a6d75cf89769925a92b9ab880d7b8"
    )


def test_catalog_preserves_machine_metadata_and_categories() -> None:
    catalog = read_machine_catalog(FIXTURES / "Devices-minimal.xml", "Mazak01")

    assert catalog.description["manufacturer"] == "Mazak"
    assert [(entry.name, entry.category) for entry in catalog.entries] == [
        ("Srpm", "SAMPLE"),
        ("execution", "EVENT"),
        ("servo_cond", "CONDITION"),
    ]
    spindle = catalog.entries[0]
    assert spindle.units == "REVOLUTION/MINUTE"
    assert spindle.component_path[-1] == "Rotary:C"


def test_decoder_retains_categories_unknown_invalid_and_locators(
    tmp_path: Path, representative_raw_bytes: bytes
) -> None:
    raw_path = tmp_path / "raw.shdr"
    raw_path.write_bytes(representative_raw_bytes)
    catalog = read_machine_catalog(FIXTURES / "Devices-minimal.xml", "Mazak01")

    records = list(RawRecordDecoder(catalog).decode(raw_path, "sha256:fixture"))

    assert [record.parse_status for record in records] == [
        ParseStatus.PARSED,
        ParseStatus.PARSED,
        ParseStatus.PARSED,
        ParseStatus.UNKNOWN_DATA_ITEM,
        ParseStatus.INVALID,
    ]
    assert [record.category for record in records[:3]] == ["SAMPLE", "EVENT", "CONDITION"]
    assert records[2].value_fields_raw == ("Normal", "", "", "", "")
    assert records[3].value_fields_raw == ("10",)
    assert records[4].parse_error == "MALFORMED_FIELDS"
    assert records[0].byte_start == 0
    assert records[-1].byte_end == len(representative_raw_bytes)
    assert records[3].raw_payload_ref.startswith("sha256:fixture#bytes=")


def test_decoder_marks_invalid_utf8_without_losing_locator(tmp_path: Path) -> None:
    raw_path = tmp_path / "invalid.shdr"
    raw_path.write_bytes(b"\xff|name|value\n")
    catalog = read_machine_catalog(FIXTURES / "Devices-minimal.xml", "Mazak01")

    record = next(RawRecordDecoder(catalog).decode(raw_path, "sha256:fixture"))

    assert record.parse_status is ParseStatus.INVALID
    assert record.parse_error == "INVALID_UTF8:0"
    assert record.byte_start == 0
    assert record.byte_end == 13
