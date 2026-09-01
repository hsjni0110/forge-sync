"""Observation v2 replay identity encoder."""

from __future__ import annotations

import json
from datetime import datetime
from typing import Any
from uuid import UUID

from ...domain import ReplayObservation, ReplaySequence


class JsonReplayEnvelopeEncoder:
    def add_replay_identity(
        self,
        observation: ReplayObservation,
        replay_session_id: UUID,
        replay_sequence: ReplaySequence,
        replay_published_at: datetime,
    ) -> bytes:
        document: dict[str, Any] = json.loads(observation.canonical_envelope)
        if "replay" in document:
            raise ValueError("Canonical observation already contains replay identity")
        document["replay"] = {
            "replaySessionId": str(replay_session_id),
            "replaySequence": replay_sequence.value,
            "replayPublishedAt": _format_time(replay_published_at),
        }
        return json.dumps(document, separators=(",", ":"), sort_keys=True).encode()


def _format_time(value: datetime) -> str:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("replay published at must include a timezone")
    rendered = value.isoformat()
    return rendered[:-6] + "Z" if rendered.endswith("+00:00") else rendered
