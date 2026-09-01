"""HTTP endpoints for simulator availability only."""

from typing import Literal, TypedDict

from fastapi import APIRouter


class HealthResponse(TypedDict):
    service: Literal["forgesync-virtual-controller"]
    status: Literal["UP"]


router = APIRouter()


@router.get("/health")
def health() -> HealthResponse:
    return {"service": "forgesync-virtual-controller", "status": "UP"}
