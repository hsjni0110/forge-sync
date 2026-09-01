"""FastAPI bootstrap for the intelligence service."""

from fastapi import FastAPI

from .adapter.inbound.http import router


def create_app() -> FastAPI:
    app = FastAPI(title="ForgeSync AI Service", version="0.1.0")
    app.include_router(router)
    return app


app = create_app()
