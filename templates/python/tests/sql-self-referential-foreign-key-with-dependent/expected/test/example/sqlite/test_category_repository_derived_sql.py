# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.category_repository_models import (
    Category,
)
from generated.example.sqlite.category_repository_aiosqlite import CategoryRepositoryAiosqliteService
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def category_repository_service() -> AsyncIterator[CategoryRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield CategoryRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(category_repository_service: CategoryRepositoryAiosqliteService) -> None:
    entity_id_result = await category_repository_service.create_category(name=None, parent_category_id=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await category_repository_service.get_category(id=entity_id)
    assert isinstance(fetched, Category)
    assert fetched.name is None
    assert fetched.parent_category_id is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await category_repository_service.create_category(
            name=None, parent_category_id=None, transaction=connection
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await category_repository_service.get_category(id=entity_id, transaction=connection)
        assert isinstance(fetched, Category)
        assert fetched.name is None
        assert fetched.parent_category_id is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await category_repository_service.get_category(id=entity_id)
    assert isinstance(fetched_after_commit, Category)
    assert fetched_after_commit.name is None
    assert fetched_after_commit.parent_category_id is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await category_repository_service.create_category(
        name=None, parent_category_id=None, transaction=connection
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await category_repository_service.get_category(id=entity_id)
    assert missing is None
