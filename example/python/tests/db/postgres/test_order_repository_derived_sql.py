# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from testcontainers.postgres import PostgresContainer

from generated.db.order_repository_protocol import (
    GetOrderRecordResult,
)
from generated.db.postgres.order_repository_psycopg import OrderRepositoryPsycopgService
from generated.db.postgres.psycopg_migrations import PsycopgMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


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
    migration_service = PsycopgMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield OrderRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(order_repository_service: OrderRepositoryPsycopgService) -> None:
    entity_id = await order_repository_service.create_order_record(
        label="integration-label", status="approved", priority=3
    )
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await order_repository_service.get_order_record(id=entity_id)
    assert isinstance(fetched, GetOrderRecordResult)
    assert fetched.label == "integration-label"
    assert fetched.status == "approved"
    assert fetched.priority == 3


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(order_repository_service: OrderRepositoryPsycopgService) -> None:
    connection = order_repository_service._connection
    async with connection.transaction() as tx:
        entity_id = await order_repository_service.create_order_record(
            label="integration-label", status="approved", priority=3, transaction=tx
        )
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await order_repository_service.get_order_record(id=entity_id, transaction=tx)
        assert isinstance(fetched, GetOrderRecordResult)
        assert fetched.label == "integration-label"
        assert fetched.status == "approved"
        assert fetched.priority == 3

    fetched_after_commit = await order_repository_service.get_order_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetOrderRecordResult)
    assert fetched_after_commit.label == "integration-label"
    assert fetched_after_commit.status == "approved"
    assert fetched_after_commit.priority == 3


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
            entity_id = await order_repository_service.create_order_record(
                label="integration-label", status="approved", priority=3, transaction=tx
            )
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await order_repository_service.get_order_record(id=entity_id)
    assert missing is None
