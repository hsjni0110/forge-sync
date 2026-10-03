"""Deterministic delivery faults for one replay: which envelopes repeat and which arrive late."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class FaultScenario:
    name: str
    seed: int
    duplicate_rate: float
    reorder_rate: float
    max_delay: int


@dataclass(frozen=True, slots=True)
class EnvelopeFault:
    """`delay` counts later envelopes published before this one; 0 keeps source order."""

    is_duplicated: bool
    delay: int


def fault_for(scenario: FaultScenario, index: int, envelope_count: int) -> EnvelopeFault:
    is_duplicated = _unit_draw(scenario.seed, index, "duplicate") < scenario.duplicate_rate
    # A delayed envelope is released after `delay` later ones, so the final envelopes are
    # never held: holding them would turn a reorder into a loss when the replay ends.
    can_be_delayed = index < envelope_count - scenario.max_delay
    is_delayed = can_be_delayed and _unit_draw(scenario.seed, index, "reorder") < (
        scenario.reorder_rate
    )
    delay = 1 + int(_unit_draw(scenario.seed, index, "delay") * scenario.max_delay)
    return EnvelopeFault(is_duplicated=is_duplicated, delay=delay if is_delayed else 0)


def _unit_draw(seed: int, index: int, purpose: str) -> float:
    """A value in [0, 1) fixed by seed, index and purpose, independent of call order."""
    digest = hashlib.sha256(f"{seed}:{index}:{purpose}".encode()).digest()
    return int.from_bytes(digest[:8], "big") / 2**64
