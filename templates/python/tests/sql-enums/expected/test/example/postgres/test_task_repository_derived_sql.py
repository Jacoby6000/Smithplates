# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.task_repository_models import (
    Task,
)
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from generated.example.postgres.task_repository_psycopg import TaskRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def task_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[TaskRepositoryPsycopgService]:
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
        yield TaskRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(task_repository_service: TaskRepositoryPsycopgService) -> None:
    entity_id_result = await task_repository_service.create_task(
        id="668b917c-481f-3eaf-a504-a0b883aaff58", label=None, status=None, priority=None
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await task_repository_service.get_task(id=entity_id)
    assert isinstance(fetched, Task)
    assert fetched.id == "668b917c-481f-3eaf-a504-a0b883aaff58"
    assert fetched.label is None
    assert fetched.status is None
    assert fetched.priority is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(task_repository_service: TaskRepositoryPsycopgService) -> None:
    connection = task_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await task_repository_service.create_task(
            id="668b917c-481f-3eaf-a504-a0b883aaff58", label=None, status=None, priority=None, transaction=tx
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await task_repository_service.get_task(id=entity_id, transaction=tx)
        assert isinstance(fetched, Task)
        assert fetched.id == "668b917c-481f-3eaf-a504-a0b883aaff58"
        assert fetched.label is None
        assert fetched.status is None
        assert fetched.priority is None

    fetched_after_commit = await task_repository_service.get_task(id=entity_id)
    assert isinstance(fetched_after_commit, Task)
    assert fetched_after_commit.id == "668b917c-481f-3eaf-a504-a0b883aaff58"
    assert fetched_after_commit.label is None
    assert fetched_after_commit.status is None
    assert fetched_after_commit.priority is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(task_repository_service: TaskRepositoryPsycopgService) -> None:
    connection = task_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await task_repository_service.create_task(
                id="668b917c-481f-3eaf-a504-a0b883aaff58", label=None, status=None, priority=None, transaction=tx
            )
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await task_repository_service.get_task(id=entity_id)
    assert missing is None
