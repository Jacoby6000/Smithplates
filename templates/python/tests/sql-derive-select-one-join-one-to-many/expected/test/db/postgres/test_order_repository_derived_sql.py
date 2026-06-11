# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator

import psycopg
import pytest
import pytest_asyncio
from order_repository_protocol import (
    GetOrderResult,
)
from order_repository_psycopg import OrderRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

SCHEMA_DDL = """-- example#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID,
    sku TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);"""


async def _apply_schema_ddl(connection: psycopg.AsyncConnection, schema_ddl: str) -> None:
    for statement in schema_ddl.split(";"):
        ddl_statement = statement.strip()
        if ddl_statement:
            _ = await connection.execute(f"{ddl_statement};")


@pytest_asyncio.fixture
async def order_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[OrderRepositoryPsycopgService]:
    connection = await psycopg.AsyncConnection.connect(
        host=postgres_container.get_container_host_ip(),
        port=int(postgres_container.get_exposed_port(5432)),
        user=postgres_container.username,
        password=postgres_container.password,
        dbname=postgres_container.dbname,
    )
    await _apply_schema_ddl(connection, SCHEMA_DDL)
    try:
        yield OrderRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(order_repository_service: OrderRepositoryPsycopgService) -> None:
    entity_id = await order_repository_service.create_order(label="integration-label")
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched, GetOrderResult)
    assert fetched.label == "integration-label"


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(order_repository_service: OrderRepositoryPsycopgService) -> None:
    connection = order_repository_service._connection
    async with connection.transaction() as tx:
        entity_id = await order_repository_service.create_order(label="integration-label", transaction=tx)
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await order_repository_service.get_order(id=entity_id, transaction=tx)
        assert isinstance(fetched, GetOrderResult)
        assert fetched.label == "integration-label"

    fetched_after_commit = await order_repository_service.get_order(id=entity_id)
    assert isinstance(fetched_after_commit, GetOrderResult)
    assert fetched_after_commit.label == "integration-label"


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    order_repository_service: OrderRepositoryPsycopgService,
) -> None:
    connection = order_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id = await order_repository_service.create_order(label="integration-label", transaction=tx)
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await order_repository_service.get_order(id=entity_id)
    assert missing is None
