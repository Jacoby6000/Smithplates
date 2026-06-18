# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.db.models.order_repository_models import (
    CreateOrderResult,
)
from generated.db.order_repository_protocol import (
    GetOrderResult,
)
from generated.db.sqlite.order_repository_aiosqlite import OrderRepositoryAiosqliteService
from generated.db.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def order_repository_service() -> AsyncIterator[OrderRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield OrderRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(order_repository_service: OrderRepositoryAiosqliteService) -> None:
    created = await order_repository_service.create_order(label=None)
    assert isinstance(created, CreateOrderResult)
    entity_id = created.id
    assert entity_id

    fetched = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched, GetOrderResult)
    assert fetched.label is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    order_repository_service: OrderRepositoryAiosqliteService,
) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    try:
        created = await order_repository_service.create_order(label=None, transaction=connection)
        assert isinstance(created, CreateOrderResult)
        entity_id = created.id
        assert entity_id

        fetched = await order_repository_service.get_order(id=entity_id, transaction=connection)
        assert isinstance(fetched, GetOrderResult)
        assert fetched.label is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched_after_commit, GetOrderResult)
    assert fetched_after_commit.label is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    order_repository_service: OrderRepositoryAiosqliteService,
) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    created = await order_repository_service.create_order(label=None, transaction=connection)
    assert isinstance(created, CreateOrderResult)
    entity_id = created.id
    assert entity_id
    await connection.rollback()

    missing = await order_repository_service.get_order(id=entity_id)
    assert missing is None
