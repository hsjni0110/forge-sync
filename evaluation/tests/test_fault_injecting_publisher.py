from __future__ import annotations

from forgesync_evaluation.adapter.outbound.fault_injecting_publisher import (
    FaultInjectingPublisher,
)
from forgesync_evaluation.domain.fault_plan import EnvelopeFault

IN_ORDER = EnvelopeFault(is_duplicated=False, delay=0)


class RecordingPublisher:
    def __init__(self) -> None:
        self.published: list[bytes] = []

    def publish(self, envelope: bytes) -> None:
        self.published.append(envelope)


def test_publisher_repeats_identical_bytes_and_releases_held_envelopes_after_their_delay() -> None:
    planned = {
        1: EnvelopeFault(is_duplicated=True, delay=2),
        2: EnvelopeFault(is_duplicated=True, delay=0),
    }
    inner = RecordingPublisher()
    publisher = FaultInjectingPublisher(inner, lambda index: planned.get(index, IN_ORDER))

    for envelope in (b"0", b"1", b"2", b"3", b"4"):
        publisher.publish(envelope)

    assert inner.published == [b"0", b"2", b"2", b"3", b"1", b"1", b"4"]


def test_publisher_without_faults_passes_every_envelope_through_in_order() -> None:
    inner = RecordingPublisher()
    publisher = FaultInjectingPublisher(inner, lambda _: IN_ORDER)

    for envelope in (b"0", b"1", b"2"):
        publisher.publish(envelope)

    assert inner.published == [b"0", b"1", b"2"]
