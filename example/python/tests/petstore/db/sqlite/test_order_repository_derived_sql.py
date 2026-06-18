# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio

from generated.petstore.db.order_repository_protocol import (
    GetOrderRecordResult,
)
from generated.petstore.db.sqlite.order_repository_aiosqlite import OrderRepositoryAiosqliteService
from generated.petstore.db.sqlite.sqlite_migrations import SqliteMigrationService

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
    entity_id_result = await order_repository_service.create_order_record(
        label="integration-label", status="approved", priority=3
    )
    entity_id = entity_id_result.id
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await order_repository_service.get_order_record(id=entity_id)
    assert isinstance(fetched, GetOrderRecordResult)
    assert fetched.label == "integration-label"
    assert fetched.status == "approved"
    assert fetched.priority == 3


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    order_repository_service: OrderRepositoryAiosqliteService,
) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await order_repository_service.create_order_record(
            label="integration-label", status="approved", priority=3, transaction=connection
        )
        entity_id = entity_id_result.id
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await order_repository_service.get_order_record(id=entity_id, transaction=connection)
        assert isinstance(fetched, GetOrderRecordResult)
        assert fetched.label == "integration-label"
        assert fetched.status == "approved"
        assert fetched.priority == 3
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await order_repository_service.get_order_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetOrderRecordResult)
    assert fetched_after_commit.label == "integration-label"
    assert fetched_after_commit.status == "approved"
    assert fetched_after_commit.priority == 3


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    order_repository_service: OrderRepositoryAiosqliteService,
) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await order_repository_service.create_order_record(
        label="integration-label", status="approved", priority=3, transaction=connection
    )
    entity_id = entity_id_result.id
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await order_repository_service.get_order_record(id=entity_id)
    assert missing is None
