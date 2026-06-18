# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from testcontainers.postgres import PostgresContainer

from generated.petstore.db.category_repository_protocol import (
    GetCategoryRecordResult,
)
from generated.petstore.db.postgres.category_repository_psycopg import CategoryRepositoryPsycopgService
from generated.petstore.db.postgres.psycopg_migrations import PsycopgMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def category_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[CategoryRepositoryPsycopgService]:
    connection = await psycopg.AsyncConnection.connect(
        host=postgres_container.get_container_host_ip(),
        port=int(postgres_container.get_exposed_port(5432)),
        user=postgres_container.username,
        password=postgres_container.password,
        dbname=postgres_container.dbname,
    )
    migration_service = PsycopgMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d511c78e-cf3b-3fd2-9037-db356d2b78f1', 'integration-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d743e19a-1ec3-375f-ab9d-c89e5d0fc587', 'integration-updated-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.commit()
    try:
        yield CategoryRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(category_repository_service: CategoryRepositoryPsycopgService) -> None:
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
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    category_repository_service: CategoryRepositoryPsycopgService,
) -> None:
    connection = category_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await category_repository_service.create_category_record(
            name="integration-name", store_id="d511c78e-cf3b-3fd2-9037-db356d2b78f1", transaction=tx
        )
        entity_id = entity_id_result.id
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await category_repository_service.get_category_record(id=entity_id, transaction=tx)
        assert isinstance(fetched, GetCategoryRecordResult)
        assert fetched.name == "integration-name"
        assert fetched.store_id == "d511c78e-cf3b-3fd2-9037-db356d2b78f1"

    fetched_after_commit = await category_repository_service.get_category_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetCategoryRecordResult)
    assert fetched_after_commit.name == "integration-name"
    assert fetched_after_commit.store_id == "d511c78e-cf3b-3fd2-9037-db356d2b78f1"


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    category_repository_service: CategoryRepositoryPsycopgService,
) -> None:
    connection = category_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await category_repository_service.create_category_record(
                name="integration-name", store_id="d511c78e-cf3b-3fd2-9037-db356d2b78f1", transaction=tx
            )
            entity_id = entity_id_result.id
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await category_repository_service.get_category_record(id=entity_id)
    assert missing is None
