"""HTTP endpoints that expose service availability without domain claims."""

from typing import Literal, TypedDict

from fastapi import APIRouter


class HealthResponse(TypedDict):
    service: Literal["forgesync-ai-service"]
    status: Literal["UP"]


router = APIRouter()


@router.get("/health")
def health() -> HealthResponse:
    return {"service": "forgesync-ai-service", "status": "UP"}
