# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from generated.example.postgres.record_repository_psycopg import RecordRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def record_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[RecordRepositoryPsycopgService]:
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
        yield RecordRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(record_repository_service: RecordRepositoryPsycopgService) -> None:
    entity_id_result = await record_repository_service.insert_record(metadata={"integration": True})
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await record_repository_service.get_record_by_id(id=entity_id)
    assert isinstance(fetched, Record)
    assert fetched.metadata == {"integration": True}


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    record_repository_service: RecordRepositoryPsycopgService,
) -> None:
    connection = record_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await record_repository_service.insert_record(metadata={"integration": True}, transaction=tx)
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await record_repository_service.get_record_by_id(id=entity_id, transaction=tx)
        assert isinstance(fetched, Record)
        assert fetched.metadata == {"integration": True}

    fetched_after_commit = await record_repository_service.get_record_by_id(id=entity_id)
    assert isinstance(fetched_after_commit, Record)
    assert fetched_after_commit.metadata == {"integration": True}


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    record_repository_service: RecordRepositoryPsycopgService,
) -> None:
    connection = record_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await record_repository_service.insert_record(
                metadata={"integration": True}, transaction=tx
            )
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await record_repository_service.get_record_by_id(id=entity_id)
    assert missing is None
