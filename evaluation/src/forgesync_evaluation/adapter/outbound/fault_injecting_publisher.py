"""Wrap the replay publisher to repeat or delay envelopes for evaluation runs only."""

from __future__ import annotations

from collections.abc import Callable
from typing import Protocol

from forgesync_evaluation.domain.fault_plan import EnvelopeFault


class EnvelopePublisher(Protocol):
    def publish(self, envelope: bytes) -> None: ...


class FaultInjectingPublisher:
    def __init__(self, inner: EnvelopePublisher, plan: Callable[[int], EnvelopeFault]) -> None:
        self._inner = inner
        self._plan = plan
        self._next_index = 0
        self._held: list[tuple[int, bytes, EnvelopeFault]] = []

    def publish(self, envelope: bytes) -> None:
        index = self._next_index
        self._next_index += 1
        fault = self._plan(index)
        if fault.delay > 0:
            self._held.append((index + fault.delay, envelope, fault))
        else:
            self._deliver(envelope, fault)
        self._release_due(index)

    def _release_due(self, index: int) -> None:
        due = [held for held in self._held if held[0] <= index]
        self._held = [held for held in self._held if held[0] > index]
        for _, envelope, fault in due:
            self._deliver(envelope, fault)

    def _deliver(self, envelope: bytes, fault: EnvelopeFault) -> None:
        # A QoS1 redelivery carries the same bytes, so the duplicate keeps every identity.
        self._inner.publish(envelope)
        if fault.is_duplicated:
            self._inner.publish(envelope)
