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

    assert (acquire_exit, verify_exit, profile_exit) == (0, 0, 0)
    assert (output_path / "profile.json").is_file()
    assert (output_path / "profile.md").is_file()
    output_lines = capsys.readouterr().out.splitlines()  # type: ignore[attr-defined]
    assert [json.loads(line)["command"] for line in output_lines] == [
        "acquire",
        "verify",
        "profile",
    ]
