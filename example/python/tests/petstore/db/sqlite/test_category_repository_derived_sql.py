# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio

from generated.petstore.db.category_repository_protocol import (
    GetCategoryRecordResult,
)
from generated.petstore.db.sqlite.category_repository_aiosqlite import CategoryRepositoryAiosqliteService
from generated.petstore.db.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def category_repository_service() -> AsyncIterator[CategoryRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d511c78e-cf3b-3fd2-9037-db356d2b78f1', 'integration-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d743e19a-1ec3-375f-ab9d-c89e5d0fc587', 'integration-updated-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.commit()
    try:
        yield CategoryRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(category_repository_service: CategoryRepositoryAiosqliteService) -> None:
    entity_id_result = await category_repository_service.create_category_record(
        name="integration-name", store_id="d511c78e-cf3b-3fd2-9037-db356d2b78f1"
    )
    entity_id = entity_id_result.id
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await category_repository_service.get_category_record(id=entity_id)
    assert isinstance(fetched, GetCategoryRecordResult)
    assert fetched.name == "integration-name"
    assert fetched.store_id == "d511c78e-cf3b-3fd2-9037-db356d2b78f1"


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await category_repository_service.create_category_record(
            name="integration-name", store_id="d511c78e-cf3b-3fd2-9037-db356d2b78f1", transaction=connection
        )
        entity_id = entity_id_result.id
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await category_repository_service.get_category_record(id=entity_id, transaction=connection)
        assert isinstance(fetched, GetCategoryRecordResult)
        assert fetched.name == "integration-name"
        assert fetched.store_id == "d511c78e-cf3b-3fd2-9037-db356d2b78f1"
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await category_repository_service.get_category_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetCategoryRecordResult)
    assert fetched_after_commit.name == "integration-name"
    assert fetched_after_commit.store_id == "d511c78e-cf3b-3fd2-9037-db356d2b78f1"


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    category_repository_service: CategoryRepositoryAiosqliteService,
) -> None:
    connection = category_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await category_repository_service.create_category_record(
        name="integration-name", store_id="d511c78e-cf3b-3fd2-9037-db356d2b78f1", transaction=connection
    )
    entity_id = entity_id_result.id
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await category_repository_service.get_category_record(id=entity_id)
    assert missing is None
