"""Collect what ForgeSync ended with after one faulted or fault-free replay."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

JsonDocument = dict[str, Any]


class DeliverySource(Protocol):
    def project_state_intervals(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument: ...

    def segment_machining_runs(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument: ...

    def current_twin(self) -> JsonDocument: ...

    def find_alarms(self, replay_session_id: str, through_replay_sequence: int) -> JsonDocument: ...


@dataclass(frozen=True, slots=True)
class DeliveryDocuments:
    twin: JsonDocument
    intervals: JsonDocument
    machining_runs: JsonDocument
    alarms: JsonDocument


def collect_delivery_documents(
    source: DeliverySource, replay_session_id: str, through_replay_sequence: int
) -> DeliveryDocuments:
    intervals = source.project_state_intervals(replay_session_id, through_replay_sequence)
    # Same cursor alignment as Track A: every projection reads the interval-resolved cursor.
    resolved_through = int(intervals["throughReplaySequence"])
    return DeliveryDocuments(
        twin=source.current_twin(),
        intervals=intervals,
        machining_runs=source.segment_machining_runs(replay_session_id, resolved_through),
        alarms=source.find_alarms(replay_session_id, resolved_through),
    )
