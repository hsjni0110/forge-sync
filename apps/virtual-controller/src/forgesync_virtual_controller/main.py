"""FastAPI bootstrap for the virtual controller."""

from fastapi import FastAPI

from .adapter.inbound.http import router


def create_app() -> FastAPI:
    app = FastAPI(title="ForgeSync Virtual Controller", version="0.1.0")
    app.include_router(router)
    return app


app = create_app()
