from tests.e2e.support.publish_observation import (
    GUIDED_DEMO_EXPECTED_OBSERVATION_COUNT,
    SCENARIO_PATH,
    _load_object,
    _read_guided_demo_observations,
)


def test_guided_demo_uses_a_longer_ordered_real_data_excerpt_with_repeated_transitions() -> None:
    observations = _read_guided_demo_observations(_load_object(SCENARIO_PATH))

    assert len(observations) == GUIDED_DEMO_EXPECTED_OBSERVATION_COUNT
    assert all("replay" not in item for item in observations)
    assert (
        observations[0]["source"]["sourceObservedAt"]
        < observations[-1]["source"]["sourceObservedAt"]
    )
    execution_values = [
        item["payload"]["value"]
        for item in observations
        if item["provenance"]["transformation"]["sourceDataItemId"] == "Mazak01-path_13"
        and "value" in item["payload"]
    ]
    assert execution_values.count("ACTIVE") >= 3
    assert execution_values.count("READY") >= 3
    assert "FEED_HOLD" in execution_values
    assert "INTERRUPTED" in execution_values
    spindle_values = [
        item["payload"]["value"]
        for item in observations
        if item["provenance"]["transformation"]["sourceDataItemId"] == "Mazak01-C_5"
        and "value" in item["payload"]
    ]
    assert 0.0 in spindle_values
    assert any(value > 1_000 for value in spindle_values)
