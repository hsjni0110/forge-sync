"""Internal HTTP adapter for Replay control; Factory API is the public browser boundary."""

from __future__ import annotations

import os
from collections.abc import Callable
from datetime import datetime
from pathlib import Path
from typing import Annotated
from uuid import UUID

import uvicorn
from fastapi import APIRouter, FastAPI, HTTPException
from fastapi import Path as ApiPath
from pydantic import BaseModel, ConfigDict, Field

from ...application.runtime import (
    ReplayConflictError,
    ReplayNotFoundError,
    ReplayRuntime,
    ReplaySessionView,
)
from ...domain import ReplaySpeed
from ..outbound import FilesystemReplaySourceReader, JsonReplayEnvelopeEncoder
from ..outbound.mqtt import (
    MqttObservationContract,
    MqttReplayPublisher,
    ObservationSchemaValidator,
    PahoMqttConfig,
    PahoMqttV5Transport,
    SystemSleeper,
)

MachineId = Annotated[str, Field(pattern=r"^[A-Za-z0-9._-]{1,64}$")]
SourceSetId = Annotated[str, Field(pattern=r"^[A-Za-z0-9._-]{1,128}$")]


class PrepareReplayRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    machineId: MachineId
    sourceSetId: SourceSetId
    speedMultiplier: int


class RevisionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expectedRevision: int = Field(ge=0)


class StartReplayRequest(RevisionRequest):
    seekTarget: datetime | None = None


class ChangeSpeedRequest(RevisionRequest):
    speedMultiplier: int


class PrepareReplacementRequest(ChangeSpeedRequest):
    seekTarget: datetime


def create_app(runtime: ReplayRuntime) -> FastAPI:
    app = FastAPI(title="ForgeSync Replay Edge Adapter", version="0.1.0")
    router = APIRouter(prefix="/internal/v1")

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @router.post("/replay-sessions", status_code=201)
    def prepare(request: PrepareReplayRequest) -> dict[str, object]:
        return _invoke(
            lambda: runtime.prepare(
                request.machineId,
                request.sourceSetId,
                ReplaySpeed.from_multiplier(request.speedMultiplier),
            )
        )

    @router.get("/machines/{machine_id}/replay-session")
    def current(
        machine_id: Annotated[str, ApiPath(pattern=r"^[A-Za-z0-9._-]{1,64}$")],
    ) -> dict[str, object]:
        return _invoke(lambda: runtime.current(machine_id))

    @router.post("/replay-sessions/{session_id}/start")
    def start(session_id: UUID, request: StartReplayRequest) -> dict[str, object]:
        return _invoke(
            lambda: runtime.start(session_id, request.expectedRevision, request.seekTarget)
        )

    @router.post("/replay-sessions/{session_id}/replacement", status_code=201)
    def replace(session_id: UUID, request: PrepareReplacementRequest) -> dict[str, object]:
        return _invoke(
            lambda: runtime.replace(
                session_id,
                request.expectedRevision,
                ReplaySpeed.from_multiplier(request.speedMultiplier),
                request.seekTarget,
            )
        )

    @router.post("/replay-sessions/{session_id}/pause")
    def pause(session_id: UUID, request: RevisionRequest) -> dict[str, object]:
        return _invoke(lambda: runtime.pause(session_id, request.expectedRevision))

    @router.post("/replay-sessions/{session_id}/resume")
    def resume(session_id: UUID, request: RevisionRequest) -> dict[str, object]:
        return _invoke(lambda: runtime.resume(session_id, request.expectedRevision))

    @router.put("/replay-sessions/{session_id}/speed")
    def speed(session_id: UUID, request: ChangeSpeedRequest) -> dict[str, object]:
        return _invoke(
            lambda: runtime.change_speed(
                session_id,
                request.expectedRevision,
                ReplaySpeed.from_multiplier(request.speedMultiplier),
            )
        )

    app.include_router(router)
    return app


def _invoke(action: Callable[[], ReplaySessionView]) -> dict[str, object]:
    try:
        view = action()
    except ReplayNotFoundError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ReplayConflictError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return _response(view)


def _response(view: ReplaySessionView) -> dict[str, object]:
    response: dict[str, object] = {
        "schemaVersion": "1.0.0",
        "replaySessionId": str(view.replay_session_id),
        "machineId": view.machine_id,
        "sourceSetId": view.source_set_id,
        "status": view.status,
        "speedMultiplier": view.speed_multiplier,
        "revision": view.revision,
        "sourceRange": {
            "startsAt": view.source_starts_at,
            "endsAt": view.source_ends_at,
        },
    }
    if view.publication_cursor is not None:
        response["publicationCursor"] = {
            "replaySequence": view.publication_cursor.replay_sequence,
            "sourceObservedAt": view.publication_cursor.source_observed_at,
            "replayPublishedAt": view.publication_cursor.replay_published_at,
        }
    if view.failure is not None:
        response["failure"] = {
            "code": "REPLAY_PUBLICATION_FAILED",
            "message": view.failure,
            "retryable": True,
        }
    return response


def configured_app() -> FastAPI:
    source_path = Path(_required_environment("FORGESYNC_REPLAY_SOURCE_PATH"))
    source_set_id = os.getenv("FORGESYNC_REPLAY_SOURCE_SET_ID", "nist-mazak01-20161005")
    repository_root = Path(_required_environment("FORGESYNC_REPOSITORY_ROOT"))
    validator = ObservationSchemaValidator.from_path(
        repository_root / "contracts/observation-envelope/v2/observation-envelope.schema.json"
    )
    transport = PahoMqttV5Transport(
        PahoMqttConfig(
            host=os.getenv("FORGESYNC_MQTT_HOST", "127.0.0.1"),
            port=int(os.getenv("FORGESYNC_MQTT_PORT", "1883")),
            client_id=os.getenv("FORGESYNC_MQTT_CLIENT_ID", "forgesync-replay-edge"),
        )
    )
    publisher = MqttReplayPublisher(transport, MqttObservationContract(validator), SystemSleeper())
    return create_app(
        ReplayRuntime(
            {source_set_id: source_path},
            publisher,
            FilesystemReplaySourceReader(),
            JsonReplayEnvelopeEncoder(),
        )
    )


def _required_environment(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"{name} must be configured")
    return value


def main() -> None:
    uvicorn.run(
        configured_app(),
        host="127.0.0.1",
        port=int(os.getenv("FORGESYNC_REPLAY_API_PORT", "8002")),
    )


if __name__ == "__main__":
    main()
