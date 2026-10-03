from __future__ import annotations

from forgesync_evaluation.domain.fault_plan import EnvelopeFault, FaultScenario, fault_for

ENVELOPES = 20_000
COMBINED = FaultScenario("combined", seed=1, duplicate_rate=0.05, reorder_rate=0.05, max_delay=50)


def faults(scenario: FaultScenario) -> list[EnvelopeFault]:
    return [fault_for(scenario, index, ENVELOPES) for index in range(ENVELOPES)]


def test_faults_depend_only_on_scenario_and_envelope_index() -> None:
    assert faults(COMBINED) == faults(COMBINED)
    assert faults(COMBINED) != faults(FaultScenario("combined", 2, 0.05, 0.05, 50))


def test_fault_rates_follow_the_scenario_within_a_tenth() -> None:
    planned = faults(COMBINED)
    duplicated = sum(fault.is_duplicated for fault in planned) / ENVELOPES
    delayed = sum(fault.delay > 0 for fault in planned) / ENVELOPES

    assert 0.045 <= duplicated <= 0.055
    assert 0.045 <= delayed <= 0.055


def test_delays_stay_within_bounds_and_never_hold_the_final_envelopes() -> None:
    planned = faults(COMBINED)

    assert all(0 <= fault.delay <= COMBINED.max_delay for fault in planned)
    assert all(fault.delay == 0 for fault in planned[-COMBINED.max_delay :])


def test_baseline_scenario_injects_nothing() -> None:
    baseline = FaultScenario("baseline", seed=1, duplicate_rate=0.0, reorder_rate=0.0, max_delay=50)

    assert set(faults(baseline)) == {EnvelopeFault(is_duplicated=False, delay=0)}
