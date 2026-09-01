from __future__ import annotations

from pathlib import Path

from conftest import FIXTURES, artifact_spec, source_lock
from forgesync_edge.source_acquisition.domain import ArtifactRole
from forgesync_edge.source_acquisition.profile import SourceProfileGenerator, write_profile
from forgesync_edge.source_acquisition.shdr_decoder import RawRecordDecoder
from forgesync_edge.source_acquisition.xml_catalog import read_machine_catalog


def test_profile_is_deterministic_and_reports_quality(
    tmp_path: Path, devices_bytes: bytes, representative_raw_bytes: bytes
) -> None:
    raw_path = tmp_path / "raw.shdr"
    raw_path.write_bytes(representative_raw_bytes)
    catalog = read_machine_catalog(FIXTURES / "Devices-minimal.xml", "Mazak01")
    devices_spec = artifact_spec(
        "devicesXml", ArtifactRole.MTCONNECT_DEVICES, devices_bytes, "Devices.xml"
    )
    raw_spec = artifact_spec(
        "mazak01DailyRaw", ArtifactRole.SHDR_RAW, representative_raw_bytes, "raw.txt"
    )
    lock = source_lock(devices_spec, raw_spec)

    first = SourceProfileGenerator().generate(
        lock,
        catalog,
        RawRecordDecoder(catalog).decode(raw_path, raw_spec.artifact_id),
        devices_spec,
        raw_spec,
    )
    second = SourceProfileGenerator().generate(
        lock,
        catalog,
        RawRecordDecoder(catalog).decode(raw_path, raw_spec.artifact_id),
        devices_spec,
        raw_spec,
    )
    first_paths = write_profile(first, tmp_path / "first")
    second_paths = write_profile(second, tmp_path / "second")

    assert first == second
    assert first_paths[0].read_bytes() == second_paths[0].read_bytes()
    assert first_paths[1].read_bytes() == second_paths[1].read_bytes()
    assert first["records"]["total"] == 5
    assert first["records"]["byStatus"]["PARSED"] == 3
    assert first["unknownDataItems"][0]["name"] == "future_item"
    assert first["invalidRecords"]["count"] == 1
    assert first["semanticCoverage"]["ratio"] == 0.75
    assert first["semanticCoverage"]["canonicalMappingEvaluated"] is False
