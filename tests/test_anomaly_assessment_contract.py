import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


def test_anomaly_assessment_fixture_is_accepted_by_an_independent_consumer() -> None:
    repository_root = Path(__file__).resolve().parents[1]
    schema = json.loads(
        (
            repository_root / "contracts/process-analytics/v1/anomaly-assessments.schema.json"
        ).read_text()
    )
    fixture = json.loads(
        (
            repository_root / "tests/fixtures/process-analytics/v1/mazak01-anomaly-assessments.json"
        ).read_text()
    )
    errors = sorted(
        Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(fixture),
        key=lambda error: list(error.path),
    )

    assert errors == []
    assessment = fixture["assessments"][0]
    assert assessment["dataStatus"] == "INSUFFICIENT_DATA"
    assert assessment["lineage"]["origin"] == "DERIVED"
    assert assessment["lineage"]["inputCycleFeature"]["origin"] == "DERIVED"
    assert assessment["lineage"]["sources"][0]["provider"] == "NIST"
