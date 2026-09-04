import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


def test_machining_run_fixture_is_accepted_by_an_independent_consumer() -> None:
    repository_root = Path(__file__).parents[1]
    schema = json.loads(
        (repository_root / "contracts/process-analytics/v1/machining-runs.schema.json").read_text()
    )
    fixture = json.loads(
        (
            repository_root / "tests/fixtures/process-analytics/v1/mazak01-machining-runs.json"
        ).read_text()
    )

    violations = sorted(
        Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(fixture),
        key=lambda error: list(error.absolute_path),
    )

    assert violations == []
    assert fixture["machiningRuns"][0]["provenance"]["origin"] == "DERIVED"
    assert fixture["machiningRuns"][0]["provenance"]["source"]["provider"] == "NIST"
