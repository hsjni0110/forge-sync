from __future__ import annotations

import json
from io import BytesIO
from pathlib import Path

from forgesync_edge.source_acquisition import cli


class MemoryReader:
    def __init__(self, content_by_uri: dict[str, bytes]) -> None:
        self._content_by_uri = content_by_uri

    def open(self, source_uri: str) -> BytesIO:
        return BytesIO(self._content_by_uri[source_uri])


def test_cli_acquire_verify_and_profile(
    tmp_path: Path,
    monkeypatch: object,
    capsys: object,
    devices_bytes: bytes,
    representative_raw_bytes: bytes,
) -> None:
    import hashlib

    commit = "pinned-commit"
    devices_uri = f"https://example.test/{commit}/Devices.xml"
    raw_uri = f"https://example.test/{commit}/raw.txt"
    lock = {
        "sourceSetId": "nist-mazak01-20161005",
        "sourceName": "NIST test fixture",
        "upstreamRepository": "https://example.test/repository",
        "upstreamCommit": commit,
        "licenseOrTermsReference": "https://example.test/terms",
        "evidenceState": "DERIVED_FIXTURE",
        "artifacts": [
            {
                "alias": "devicesXml",
                "role": "MTCONNECT_DEVICES",
                "repositoryPath": "Devices.xml",
                "sourceUri": devices_uri,
                "mediaType": "application/xml",
                "expectedByteLength": len(devices_bytes),
                "sha256": hashlib.sha256(devices_bytes).hexdigest(),
            },
            {
                "alias": "mazak01DailyRaw",
                "role": "SHDR_RAW",
                "repositoryPath": "raw.txt",
                "sourceUri": raw_uri,
                "mediaType": "text/plain",
                "expectedByteLength": len(representative_raw_bytes),
                "sha256": hashlib.sha256(representative_raw_bytes).hexdigest(),
            },
        ],
    }
    lock_path = tmp_path / "source.lock.json"
    lock_path.write_text(json.dumps(lock), encoding="utf-8")
    store_path = tmp_path / "raw-store"
    output_path = tmp_path / "profile"
    canonical_path = tmp_path / "canonical"
    mapping_report_path = tmp_path / "mapping-report"
    mapping_path = tmp_path / "mapping.json"
    mapping_path.write_text(
        json.dumps(
            {
                "mappingVersion": "2.0.0",
                "sourceSetId": "nist-mazak01-20161005",
                "machineId": "Mazak01",
                "devicesArtifactId": f"sha256:{hashlib.sha256(devices_bytes).hexdigest()}",
                "rawArtifactId": (f"sha256:{hashlib.sha256(representative_raw_bytes).hexdigest()}"),
                "entries": [
                    {
                        "dataItemId": "Mazak01-C_5",
                        "componentId": "Mazak01-C",
                        "name": "Srpm",
                        "category": "SAMPLE",
                        "type": "ROTARY_VELOCITY",
                        "subType": "ACTUAL",
                        "unit": "REVOLUTION/MINUTE",
                        "derivedUnit": None,
                        "target": "SPINDLE_SPEED",
                    },
                    {
                        "dataItemId": "Mazak01-path_13",
                        "componentId": "Mazak01-path",
                        "name": "execution",
                        "category": "EVENT",
                        "type": "EXECUTION",
                        "subType": None,
                        "unit": None,
                        "derivedUnit": None,
                        "target": "EXECUTION",
                    },
                    {
                        "dataItemId": "Mazak01-base_1",
                        "componentId": "Mazak01-base",
                        "name": "servo_cond",
                        "category": "CONDITION",
                        "type": "ACTUATOR",
                        "subType": None,
                        "unit": None,
                        "derivedUnit": None,
                        "target": "SYSTEM",
                    },
                ],
            }
        ),
        encoding="utf-8",
    )
    reader = MemoryReader({devices_uri: devices_bytes, raw_uri: representative_raw_bytes})
    monkeypatch.setattr(cli, "HttpsSourceReader", lambda: reader)  # type: ignore[attr-defined]

    acquire_exit = cli.main(["acquire", "--lock", str(lock_path), "--store", str(store_path)])
    verify_exit = cli.main(["verify", "--lock", str(lock_path), "--store", str(store_path)])
    profile_exit = cli.main(
        [
            "profile",
            "--lock",
            str(lock_path),
            "--store",
            str(store_path),
            "--machine",
            "Mazak01",
            "--output",
            str(output_path),
        ]
    )
    mapping_exit = cli.main(
        [
            "map-observations",
            "--lock",
            str(lock_path),
            "--store",
            str(store_path),
            "--machine",
            "Mazak01",
            "--mapping",
            str(mapping_path),
            "--canonical-store",
            str(canonical_path),
            "--report-output",
            str(mapping_report_path),
        ]
    )

    assert (acquire_exit, verify_exit, profile_exit, mapping_exit) == (0, 0, 0, 0)
    assert (output_path / "profile.json").is_file()
    assert (output_path / "profile.md").is_file()
    assert (mapping_report_path / "mapping-report.json").is_file()
    assert (mapping_report_path / "mapping-report.md").is_file()
    mapping_report = json.loads(
        (mapping_report_path / "mapping-report.json").read_text(encoding="utf-8")
    )
    assert mapping_report["records"] == {
        "total": 5,
        "syntacticallyParsed": 4,
        "mapped": 3,
        "byStatus": {
            "INVALID_RAW_RECORD": 1,
            "MAPPED": 3,
            "UNKNOWN_DATA_ITEM": 1,
        },
    }
    output_lines = capsys.readouterr().out.splitlines()  # type: ignore[attr-defined]
    assert [json.loads(line)["command"] for line in output_lines] == [
        "acquire",
        "verify",
        "profile",
        "map-observations",
    ]
