# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.task_repository_models import (
    Task,
)
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService
from generated.example.sqlite.task_repository_aiosqlite import TaskRepositoryAiosqliteService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def task_repository_service() -> AsyncIterator[TaskRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield TaskRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(task_repository_service: TaskRepositoryAiosqliteService) -> None:
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
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(task_repository_service: TaskRepositoryAiosqliteService) -> None:
    connection = task_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await task_repository_service.create_task(
            id="668b917c-481f-3eaf-a504-a0b883aaff58", label=None, status=None, priority=None, transaction=connection
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await task_repository_service.get_task(id=entity_id, transaction=connection)
        assert isinstance(fetched, Task)
        assert fetched.id == "668b917c-481f-3eaf-a504-a0b883aaff58"
        assert fetched.label is None
        assert fetched.status is None
        assert fetched.priority is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await task_repository_service.get_task(id=entity_id)
    assert isinstance(fetched_after_commit, Task)
    assert fetched_after_commit.id == "668b917c-481f-3eaf-a504-a0b883aaff58"
    assert fetched_after_commit.label is None
    assert fetched_after_commit.status is None
    assert fetched_after_commit.priority is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    task_repository_service: TaskRepositoryAiosqliteService,
) -> None:
    connection = task_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await task_repository_service.create_task(
        id="668b917c-481f-3eaf-a504-a0b883aaff58", label=None, status=None, priority=None, transaction=connection
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await task_repository_service.get_task(id=entity_id)
    assert missing is None
