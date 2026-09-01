from pathlib import Path

import pytest
from forgesync_edge.source_acquisition.domain import ArtifactRole
from forgesync_edge.source_acquisition.errors import ConfigurationError
from forgesync_edge.source_acquisition.source_lock import load_source_lock


def test_loads_pinned_nist_source_contract() -> None:
    source_lock = load_source_lock(Path("config/sources/nist-mazak01-20161005.lock.json"))

    assert source_lock.source_set_id == "nist-mazak01-20161005"
    assert source_lock.upstream_commit == "968279f14ebe96c03c8877eeb1901cf6b4c8fbab"
    devices = source_lock.artifact_for_role(ArtifactRole.MTCONNECT_DEVICES)
    assert devices.expected_byte_length == 39450
    assert source_lock.artifact_for_role(ArtifactRole.SHDR_RAW).expected_byte_length == 4609711


def test_rejects_source_uri_that_is_not_pinned(tmp_path: Path) -> None:
    lock_path = tmp_path / "source.lock.json"
    lock_path.write_text(
        """{
          "sourceSetId":"source", "sourceName":"source",
          "upstreamRepository":"https://example.test/repository",
          "upstreamCommit":"abc", "licenseOrTermsReference":"https://example.test/terms",
          "evidenceState":"TO_VERIFY",
          "artifacts":[{
            "alias":"raw", "role":"SHDR_RAW", "repositoryPath":"raw.txt",
            "sourceUri":"https://example.test/master/raw.txt", "mediaType":"text/plain",
            "expectedByteLength":1,
            "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          }]
        }""",
        encoding="utf-8",
    )

    with pytest.raises(ConfigurationError, match="not pinned"):
        load_source_lock(lock_path)
