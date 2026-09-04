import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


def test_cycle_feature_fixture_is_accepted_by_an_independent_consumer() -> None:
    repository_root = Path(__file__).resolve().parents[1]
    schema = json.loads(
        (repository_root / "contracts/process-analytics/v1/cycle-features.schema.json").read_text()
    )
    fixture = json.loads(
        (
            repository_root / "tests/fixtures/process-analytics/v1/mazak01-cycle-features.json"
        ).read_text()
    )

    errors = sorted(
        Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(fixture),
        key=lambda error: list(error.path),
    )

    assert errors == []
    assert fixture["featureSets"][0]["provenance"]["origin"] == "DERIVED"
    assert (
        fixture["featureSets"][0]["metricFeatures"][0]["provenance"][0]["source"]["provider"]
        == "NIST"
    )
    assert fixture["featureSets"][0]["aggregationWindow"]["boundary"] == "[STARTED_AT,ENDED_AT)"
