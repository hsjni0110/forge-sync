from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID

import pytest
from forgesync_edge.ingestion.adapter.outbound.observation_json import observation_to_dict
from forgesync_edge.ingestion.domain import (
    Availability,
    ObservationEnvelope,
    ObservationSubject,
    Provenance,
    ProvenanceSource,
    SampleMetric,
    SamplePayload,
    SourceIdentity,
    TransformationProvenance,
    Unit,
)
from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY_ROOT = Path(__file__).parents[3]
SCHEMA_PATH = (
    REPOSITORY_ROOT
    / "contracts"
    / "observation-envelope"
    / "v2"
    / "observation-envelope.schema.json"
)
FIXTURES_ROOT = REPOSITORY_ROOT / "tests" / "fixtures" / "canonical" / "v2"


def _validator() -> Draft202012Validator:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=FormatChecker())


@pytest.mark.parametrize("fixture_path", sorted((FIXTURES_ROOT / "valid").glob("*.json")))
def test_shared_valid_observation_fixture_satisfies_schema(fixture_path: Path) -> None:
    document = json.loads(fixture_path.read_text(encoding="utf-8"))

    errors = list(_validator().iter_errors(document))

    assert errors == []


@pytest.mark.parametrize("fixture_path", sorted((FIXTURES_ROOT / "invalid").glob("*.json")))
def test_shared_invalid_observation_fixture_is_rejected(fixture_path: Path) -> None:
    document = json.loads(fixture_path.read_text(encoding="utf-8"))

    errors = list(_validator().iter_errors(document))

    assert errors, f"invalid contract fixture was accepted: {fixture_path.name}"


def test_edge_mapping_produces_shared_sample_fixture() -> None:
    artifact_id = "sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf"
    raw_record_id = f"{artifact_id}#line=125"
    observation = ObservationEnvelope(
        event_id=UUID("5cb3568f-c5c7-4c34-a8b9-3ee18fcbc5c1"),
        source_event_key=raw_record_id,
        machine_id="Mazak01",
        subject=ObservationSubject(component_id="Mazak01-C"),
        source=SourceIdentity(datetime(2016, 10, 5, 8, 43, 49, 514000, tzinfo=UTC)),
        provenance=Provenance(
            source=ProvenanceSource(source_set_id="nist-mazak01-20161005", artifact_id=artifact_id),
            transformation=TransformationProvenance(
                raw_record_id=raw_record_id,
                mapping_version="2.0.0",
                source_data_item_id="Mazak01-C_5",
            ),
        ),
        payload=SamplePayload(
            metric=SampleMetric.SPINDLE_SPEED,
            availability=Availability.AVAILABLE,
            value=0.0,
            unit=Unit.REVOLUTION_PER_MINUTE,
        ),
    )
    expected = json.loads(
        (FIXTURES_ROOT / "valid" / "sample-spindle-speed.json").read_text(encoding="utf-8")
    )

    produced = observation_to_dict(observation)

    assert produced == expected
    assert list(_validator().iter_errors(produced)) == []


def test_unavailable_sample_cannot_carry_invented_value() -> None:
    with pytest.raises(ValueError, match="must not invent"):
        SamplePayload(
            metric=SampleMetric.LOAD,
            availability=Availability.UNAVAILABLE,
            value=0.0,
            unit=Unit.PERCENT,
        )
