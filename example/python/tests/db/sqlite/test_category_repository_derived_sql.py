# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from category_repository_aiosqlite import CategoryRepositoryAiosqliteService
from category_repository_protocol import (
    GetCategoryRecordResult,
)
from sqlite_migrations import SqliteMigrationService

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
    entity_id = await category_repository_service.create_category_record(
        name="integration-name", store_id="integration-store_id"
    )
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await category_repository_service.get_category_record(id=entity_id)
    assert isinstance(fetched, GetCategoryRecordResult)
    assert fetched.name == "integration-name"
    assert fetched.store_id == "integration-store_id"


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id = await category_repository_service.create_category_record(
            name="integration-name", store_id="integration-store_id", transaction=connection
        )
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await category_repository_service.get_category_record(id=entity_id, transaction=connection)
        assert isinstance(fetched, GetCategoryRecordResult)
        assert fetched.name == "integration-name"
        assert fetched.store_id == "integration-store_id"
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await category_repository_service.get_category_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetCategoryRecordResult)
    assert fetched_after_commit.name == "integration-name"
    assert fetched_after_commit.store_id == "integration-store_id"


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    entity_id = await category_repository_service.create_category_record(
        name="integration-name", store_id="integration-store_id", transaction=connection
    )
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await category_repository_service.get_category_record(id=entity_id)
    assert missing is None
