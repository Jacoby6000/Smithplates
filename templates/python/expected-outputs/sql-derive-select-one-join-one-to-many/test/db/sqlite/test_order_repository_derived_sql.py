# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
import aiosqlite
from order_repository_aiosqlite import OrderRepositoryAiosqliteService
from order_repository_models import (
    Order,
    OrderLine,
)
from order_repository_protocol import (
    GetOrderResult,
    OrderRepositoryServiceProtocol,
)

SCHEMA_DDL = """-- example#Order
CREATE TABLE orders (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    order_id TEXT,
    sku TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);"""


@pytest_asyncio.fixture
async def order_repository_service() -> AsyncIterator[OrderRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    await connection.executescript(SCHEMA_DDL)
    await connection.commit()
    try:
        yield OrderRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(order_repository_service: OrderRepositoryAiosqliteService) -> None:
    entity_id = await order_repository_service.create_order(label="integration-label")
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched, GetOrderResult)
    assert fetched.label == "integration-label"

@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(order_repository_service: OrderRepositoryAiosqliteService) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id = await order_repository_service.create_order(label="integration-label", transaction=connection)
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await order_repository_service.get_order(id=entity_id, transaction=connection)
        assert isinstance(fetched, GetOrderResult)
        assert fetched.label == "integration-label"
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched_after_commit, GetOrderResult)
    assert fetched_after_commit.label == "integration-label"


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(order_repository_service: OrderRepositoryAiosqliteService) -> None:
    connection = order_repository_service._connection
    await connection.execute("BEGIN")
    entity_id = await order_repository_service.create_order(label="integration-label", transaction=connection)
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await order_repository_service.get_order(id=entity_id)
    assert missing is None
