from __future__ import annotations

import hashlib
import json
from collections import Counter
from dataclasses import replace
from pathlib import Path

import pytest
from conftest import FIXTURES
from forgesync_edge.ingestion.adapter.inbound import (
    adapt_catalog,
    adapt_records,
    load_mapping_table,
)
from forgesync_edge.ingestion.adapter.outbound import Uuid5ObservationIdGenerator
from forgesync_edge.ingestion.adapter.outbound.filesystem_mapping_output import (
    write_canonical_run,
)
from forgesync_edge.ingestion.adapter.outbound.observation_json import observation_to_dict
from forgesync_edge.ingestion.application import MapCanonicalObservations
from forgesync_edge.ingestion.application.run_identity import canonical_processing_run_id
from forgesync_edge.ingestion.domain import MappingResult, MappingStatus, MappingTable
from forgesync_edge.ingestion.domain.observation import (
    ConditionPayload,
    EventPayload,
    EventType,
    SampleMetric,
    SamplePayload,
)
from forgesync_edge.source_acquisition.shdr_decoder import PARSER_VERSION, RawRecordDecoder
from forgesync_edge.source_acquisition.xml_catalog import read_machine_catalog
from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY_ROOT = Path(__file__).parents[3]
MAPPING_PATH = REPOSITORY_ROOT / "config" / "mappings" / "nist-mazak01-observation-v2.json"
SCHEMA_PATH = (
    REPOSITORY_ROOT
    / "contracts"
    / "observation-envelope"
    / "v2"
    / "observation-envelope.schema.json"
)
MAPPING_FIXTURE = FIXTURES / "mazak01-canonical-mapping-golden.shdr"


def _mapping_results() -> tuple[MappingTable, list[MappingResult]]:
    content = MAPPING_FIXTURE.read_bytes()
    artifact_id = f"sha256:{hashlib.sha256(content).hexdigest()}"
    source_catalog = read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01")
    catalog = adapt_catalog(source_catalog)
    fixture_ids = set(catalog.by_id())
    source_table = load_mapping_table(MAPPING_PATH)
    table = replace(
        source_table,
        raw_artifact_id=artifact_id,
        entries=tuple(
            entry for entry in source_table.entries if entry.data_item.data_item_id in fixture_ids
        ),
    )
    records = RawRecordDecoder(source_catalog).decode(MAPPING_FIXTURE, artifact_id)
    use_case = MapCanonicalObservations(table, catalog, Uuid5ObservationIdGenerator())
    return table, list(use_case.map(adapt_records(records, catalog)))


def test_maps_component_aware_samples_events_and_conditions() -> None:
    _, results = _mapping_results()

    statuses = Counter(result.status for result in results)
    observations = [result.observation for result in results if result.observation is not None]

    assert statuses == {MappingStatus.MAPPED: 54}
    assert sum(isinstance(item.payload, SamplePayload) for item in observations) == 23
    assert sum(isinstance(item.payload, EventPayload) for item in observations) == 27
    assert sum(isinstance(item.payload, ConditionPayload) for item in observations) == 4
    assert (
        sum(
            getattr(item.payload, "availability", None) is not None
            and item.payload.availability.value == "UNAVAILABLE"
            for item in observations
        )
        == 22
    )


def test_golden_values_preserve_types_units_condition_and_provenance() -> None:
    _, results = _mapping_results()
    observations = [result.observation for result in results if result.observation is not None]
    spindle = next(
        item
        for item in observations
        if isinstance(item.payload, SamplePayload)
        and item.payload.metric is SampleMetric.SPINDLE_SPEED
        and item.payload.value == 49
    )
    tool = next(
        item
        for item in observations
        if isinstance(item.payload, EventPayload)
        and item.payload.event_type is EventType.TOOL_NUMBER
        and item.payload.value == 13
    )
    condition = next(
        item
        for item in observations
        if isinstance(item.payload, ConditionPayload) and item.payload.native_code == "406"
    )

    assert spindle.payload.unit.value == "REVOLUTION/MINUTE"
    assert spindle.source.agent_instance_id is None
    assert spindle.source.source_sequence is None
    assert spindle.provenance.transformation.raw_record_id == spindle.source_event_key
    assert spindle.provenance.transformation.mapping_version == "2.2.0"
    assert spindle.subject.component_id == "Mazak01-C"
    assert spindle.provenance.transformation.source_data_item_id == "Mazak01-C_5"
    assert tool.payload.value == 13
    assert condition.payload.level.value == "WARNING"
    assert condition.payload.message == "MEMORY PROTECT"

    secondary_spindle = next(
        item
        for item in observations
        if isinstance(item.payload, SamplePayload)
        and item.payload.metric is SampleMetric.SPINDLE_SPEED
        and item.payload.value == 2000
    )
    x_position = next(
        item
        for item in observations
        if isinstance(item.payload, SamplePayload)
        and item.payload.metric is SampleMetric.POSITION
        and item.payload.value == -0.00127
    )
    assert secondary_spindle.subject.component_id == "Mazak01-C2"
    assert x_position.subject.component_id == "Mazak01-X"

    b_axis_angle = next(
        item
        for item in observations
        if isinstance(item.payload, SamplePayload) and item.payload.metric is SampleMetric.ANGLE
    )
    assert b_axis_angle.payload.value == 45
    assert b_axis_angle.payload.unit.value == "DEGREE"
    assert b_axis_angle.subject.component_id == "Mazak01-B"
    assert b_axis_angle.provenance.transformation.source_data_item_id == "Mazak01-B_4"
    assert b_axis_angle.provenance.transformation.mapping_version == "2.2.0"


def test_generated_observations_satisfy_shared_contract() -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    _, results = _mapping_results()

    errors = [
        error
        for result in results
        if result.observation is not None
        for error in validator.iter_errors(observation_to_dict(result.observation))
    ]

    assert errors == []


def test_rejects_non_finite_sample_without_losing_locator() -> None:
    table, results = _mapping_results()
    mapped_feed = next(
        result
        for result in results
        if result.candidate.data_item_name_raw == "Fact" and result.observation is not None
    )
    invalid_candidate = replace(mapped_feed.candidate, value_fields_raw=("NaN",))
    source_catalog = read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01")
    use_case = MapCanonicalObservations(
        table, adapt_catalog(source_catalog), Uuid5ObservationIdGenerator()
    )

    invalid = next(use_case.map([invalid_candidate]))

    assert invalid.status is MappingStatus.INVALID_VALUE
    assert invalid.observation is None
    assert invalid.candidate.raw_record_id == invalid_candidate.raw_record_id


def test_mapping_table_rejects_catalog_metadata_drift() -> None:
    table, _ = _mapping_results()
    catalog = adapt_catalog(read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01"))
    first = replace(catalog.data_items[0], unit="DEGREE/SECOND")
    drifted = replace(catalog, data_items=(first, *catalog.data_items[1:]))

    with pytest.raises(ValueError, match="metadata differs"):
        table.validate_catalog(drifted)


def test_mapping_table_rejects_duplicate_component_channel() -> None:
    table, _ = _mapping_results()
    catalog = adapt_catalog(read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01"))
    duplicated = replace(table, entries=(*table.entries, table.entries[0]))

    with pytest.raises(ValueError, match="duplicate component/category/target"):
        duplicated.validate_catalog(catalog)


def test_event_identity_is_stable_and_changes_with_mapping_version() -> None:
    generator = Uuid5ObservationIdGenerator()
    raw_record_id = "sha256:" + "a" * 64 + "#bytes=10-20"

    first = generator.generate("2.0.0", raw_record_id)

    assert first == generator.generate("2.0.0", raw_record_id)
    assert first != generator.generate("2.1.0", raw_record_id)


def test_canonical_run_is_deterministic_and_reused(tmp_path: Path) -> None:
    table, first_results = _mapping_results()
    processing_run_id = canonical_processing_run_id(table, PARSER_VERSION)

    first = write_canonical_run(first_results, table, processing_run_id, PARSER_VERSION, tmp_path)
    _, second_results = _mapping_results()
    second = write_canonical_run(second_results, table, processing_run_id, PARSER_VERSION, tmp_path)

    assert first.status == "STORED"
    assert second.status == "REUSED_VERIFIED"
    assert first.observation_count == 54
    assert first.report == second.report
    assert first.report["records"]["byStatus"] == {  # type: ignore[index]
        "MAPPED": 54,
    }
    assert len((first.run_directory / "observations.ndjson").read_text().splitlines()) == 54


def test_maps_accumulated_time_counters_to_subtype_specific_targets() -> None:
    _, results = _mapping_results()
    observations = [result.observation for result in results if result.observation is not None]
    counters = [
        item
        for item in observations
        if isinstance(item.payload, SamplePayload)
        and item.payload.metric
        in {
            SampleMetric.TOTAL_ACCUMULATED_TIME,
            SampleMetric.AUTO_ACCUMULATED_TIME,
            SampleMetric.CUT_ACCUMULATED_TIME,
        }
        and item.payload.value is not None
    ]
    values_by_metric: dict[SampleMetric, list[float]] = {}
    for item in counters:
        values_by_metric.setdefault(item.payload.metric, []).append(item.payload.value)

    assert values_by_metric[SampleMetric.TOTAL_ACCUMULATED_TIME] == [39932651, 39932652]
    assert values_by_metric[SampleMetric.AUTO_ACCUMULATED_TIME] == [7717972]
    assert values_by_metric[SampleMetric.CUT_ACCUMULATED_TIME] == [3819274]
    assert {item.payload.unit.value for item in counters} == {"SECOND"}
    assert {item.subject.component_id for item in counters} == {"Mazak01-path"}
    assert {item.provenance.transformation.source_data_item_id for item in counters} == {
        "Mazak01-path_17",
        "Mazak01-path_18",
        "Mazak01-path_19",
    }


def test_accumulated_time_unit_is_declared_as_derived_because_catalog_omits_it() -> None:
    table, _ = _mapping_results()
    definitions = table.by_data_item_id()

    total = definitions["Mazak01-path_18"]
    spindle = definitions["Mazak01-C_5"]

    assert total.data_item.unit is None
    assert total.derived_unit == "SECOND"
    assert total.resolved_unit == "SECOND"
    assert spindle.derived_unit is None
    assert spindle.resolved_unit == "REVOLUTION/MINUTE"


def test_maps_operating_signals_without_inventing_units() -> None:
    _, results = _mapping_results()
    observations = [result.observation for result in results if result.observation is not None]
    events = {
        (
            item.provenance.transformation.source_data_item_id,
            item.payload.value,
        ): item
        for item in observations
        if isinstance(item.payload, EventPayload)
    }

    emergency_stop = events[("Mazak01-controller_4", "TRIGGERED")]
    spindle_override = events[("Mazak01-C_6", 100)]
    rapid_override = events[("Mazak01-path_8", 5)]
    programmed_override = events[("Mazak01-path_9", 100)]
    program_line = events[("Mazak01-path_3", 1)]
    sequence_number = events[("Mazak01-path_5", 1)]

    assert emergency_stop.payload.event_type is EventType.EMERGENCY_STOP
    assert emergency_stop.subject.component_id == "Mazak01-controller"
    assert spindle_override.payload.event_type is EventType.ROTARY_VELOCITY_OVERRIDE
    assert rapid_override.payload.event_type is EventType.RAPID_PATH_FEEDRATE_OVERRIDE
    assert programmed_override.payload.event_type is EventType.PROGRAMMED_PATH_FEEDRATE_OVERRIDE
    assert program_line.payload.event_type is EventType.LINE
    assert sequence_number.payload.event_type.value == "PROGRAM_SEQUENCE_NUMBER"
    assert not hasattr(spindle_override.payload, "unit")


def test_preserves_counter_regression_instead_of_correcting_it() -> None:
    table, results = _mapping_results()
    counter = next(
        result
        for result in results
        if result.candidate.data_item_name_raw == "total_time" and result.observation is not None
    )
    catalog = adapt_catalog(read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01"))
    use_case = MapCanonicalObservations(table, catalog, Uuid5ObservationIdGenerator())
    regressed = replace(counter.candidate, value_fields_raw=("1",), source_event_key="regressed")

    mapped = next(use_case.map([regressed]))

    assert mapped.status is MappingStatus.MAPPED
    assert mapped.observation is not None
    assert mapped.observation.payload.value == 1


def test_mapping_table_rejects_unitless_sample_without_declared_derived_unit() -> None:
    table, _ = _mapping_results()
    catalog = adapt_catalog(read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01"))
    definitions = table.by_data_item_id()
    undeclared = replace(definitions["Mazak01-path_18"], derived_unit=None)
    without_declared_unit = replace(
        table,
        entries=tuple(
            undeclared if entry.data_item.data_item_id == "Mazak01-path_18" else entry
            for entry in table.entries
        ),
    )

    with pytest.raises(ValueError, match="derived unit"):
        without_declared_unit.validate_catalog(catalog)


def test_mapping_table_rejects_derived_unit_that_overrides_the_catalog() -> None:
    table, _ = _mapping_results()
    catalog = adapt_catalog(read_machine_catalog(FIXTURES / "Devices-mapping.xml", "Mazak01"))
    definitions = table.by_data_item_id()
    overridden = replace(definitions["Mazak01-C_5"], derived_unit="SECOND")
    with_overridden_unit = replace(
        table,
        entries=tuple(
            overridden if entry.data_item.data_item_id == "Mazak01-C_5" else entry
            for entry in table.entries
        ),
    )

    with pytest.raises(ValueError, match="derived unit"):
        with_overridden_unit.validate_catalog(catalog)


def test_report_discloses_which_units_are_derived_rather_than_source_declared(
    tmp_path: Path,
) -> None:
    table, results = _mapping_results()
    processing_run_id = canonical_processing_run_id(table, PARSER_VERSION)

    output = write_canonical_run(results, table, processing_run_id, PARSER_VERSION, tmp_path)

    mappings = {row["dataItemId"]: row for row in output.report["mappings"]}
    assert mappings["Mazak01-path_18"]["unit"] is None
    assert mappings["Mazak01-path_18"]["derivedUnit"] == "SECOND"
    assert mappings["Mazak01-C_5"]["derivedUnit"] is None
    markdown = (output.run_directory / "mapping-report.md").read_text(encoding="utf-8")
    assert "`SECOND` (derived)" in markdown
    assert any("derived unit" in limitation for limitation in output.report["limitations"])


def test_contract_separates_a_derived_unit_from_a_source_declared_one() -> None:
    _, results = _mapping_results()
    documents = [
        observation_to_dict(result.observation)
        for result in results
        if result.observation is not None
    ]
    available_samples = {
        document["provenance"]["transformation"]["sourceDataItemId"]: document["payload"]
        for document in documents
        if document["observationKind"] == "SAMPLE"
        and document["payload"]["availability"] == "AVAILABLE"
    }
    unavailable_counter = next(
        document["payload"]
        for document in documents
        if document["provenance"]["transformation"]["sourceDataItemId"] == "Mazak01-path_18"
        and document["payload"]["availability"] == "UNAVAILABLE"
    )

    assert available_samples["Mazak01-path_18"]["unit"] == "SECOND"
    assert available_samples["Mazak01-path_18"]["unitProvenance"] == "DERIVED"
    assert available_samples["Mazak01-X_1"]["unit"] == "MILLIMETER"
    assert available_samples["Mazak01-X_1"]["unitProvenance"] == "SOURCE_DECLARED"
    assert "unitProvenance" not in unavailable_counter
