"""FastAPI application wiring generated routes to repository-backed adapters."""

from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from generated.petstore_api.api_exception_handler import DefaultFallbackApiExceptionHandler
from generated.petstore_api.app_factory import create_app
from generated.petstore_api.app_services import ApiServices

from server.api_adapters import CategoriesApiService, HealthApiService, OrdersApiService, PetsApiService
from server.database import DEFAULT_DATABASE_PATH, repository_lifespan
from server.repository_service import PetstoreRepositoryService


def _bootstrap_api_services() -> ApiServices:
    """Placeholder services replaced during application lifespan startup."""

    class _BootstrapRepositoryService(PetstoreRepositoryService):
        def __init__(self) -> None:
            raise RuntimeError("API services are not configured yet")

    bootstrap = _BootstrapRepositoryService.__new__(_BootstrapRepositoryService)
    return ApiServices(
        categories_api=CategoriesApiService(bootstrap),
        health_api=HealthApiService(),
        orders_api=OrdersApiService(bootstrap),
        pets_api=PetsApiService(bootstrap),
    )


def build_app(*, database_path: Path | None = None) -> FastAPI:
    app = create_app(
        services=_bootstrap_api_services(),
        fallback_exception_handler=DefaultFallbackApiExceptionHandler(),
    )
    if database_path is not None:
        app.state.database_path = database_path

    @asynccontextmanager
    async def lifespan(fastapi_app: FastAPI):
        db_path = getattr(fastapi_app.state, "database_path", DEFAULT_DATABASE_PATH)
        async with repository_lifespan(db_path) as repositories:
            repository_service = PetstoreRepositoryService(repositories)
            _store_id, category_id = await repository_service.seed_reference_data()
            fastapi_app.state.seed_category_id = category_id
            fastapi_app.state.api_services = ApiServices(
                categories_api=CategoriesApiService(repository_service),
                health_api=HealthApiService(),
                orders_api=OrdersApiService(repository_service),
                pets_api=PetsApiService(repository_service),
            )
            yield

    app.router.lifespan_context = lifespan
    return app


def _database_path_from_env() -> Path | None:
    configured = os.environ.get("PETSTORE_DATABASE_PATH")
    if configured is None or configured == "":
        return None
    return Path(configured)


app = build_app(database_path=_database_path_from_env())


def run() -> None:
    import uvicorn

    uvicorn.run("server.app:app", host="127.0.0.1", port=8080, reload=False)


if __name__ == "__main__":
    run()
