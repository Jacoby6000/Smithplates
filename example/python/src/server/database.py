"""Wire generated repository services to the SQLite database."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path

import aiosqlite

from category_repository_aiosqlite import CategoryRepositoryAiosqliteService
from order_repository_aiosqlite import OrderRepositoryAiosqliteService
from pet_repository_aiosqlite import PetRepositoryAiosqliteService
from sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[2] / "db" / "migrations" / "sqlite"
DEFAULT_DATABASE_PATH = Path(__file__).resolve().parents[2] / "data" / "petstore.sqlite3"


@dataclass
class RepositoryBundle:
    pets: PetRepositoryAiosqliteService
    categories: CategoryRepositoryAiosqliteService
    orders: OrderRepositoryAiosqliteService
    connection: aiosqlite.Connection


async def open_repositories(database_path: Path = DEFAULT_DATABASE_PATH) -> RepositoryBundle:
    database_path.parent.mkdir(parents=True, exist_ok=True)
    connection = await aiosqlite.connect(database_path)
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    return RepositoryBundle(
        pets=PetRepositoryAiosqliteService(connection),
        categories=CategoryRepositoryAiosqliteService(connection),
        orders=OrderRepositoryAiosqliteService(connection),
        connection=connection,
    )


@asynccontextmanager
async def repository_lifespan(database_path: Path = DEFAULT_DATABASE_PATH) -> AsyncIterator[RepositoryBundle]:
    bundle = await open_repositories(database_path)
    try:
        yield bundle
    finally:
        await bundle.connection.close()
