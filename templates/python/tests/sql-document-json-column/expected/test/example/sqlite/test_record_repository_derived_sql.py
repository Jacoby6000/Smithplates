# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.sqlite.record_repository_aiosqlite import RecordRepositoryAiosqliteService
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def record_repository_service() -> AsyncIterator[RecordRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield RecordRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(record_repository_service: RecordRepositoryAiosqliteService) -> None:
    entity_id_result = await record_repository_service.insert_record(metadata={"integration": True})
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await record_repository_service.get_record_by_id(id=entity_id)
    assert isinstance(fetched, Record)
    assert fetched.metadata == {"integration": True}


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    record_repository_service: RecordRepositoryAiosqliteService,
) -> None:
    connection = record_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await record_repository_service.insert_record(
            metadata={"integration": True}, transaction=connection
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await record_repository_service.get_record_by_id(id=entity_id, transaction=connection)
        assert isinstance(fetched, Record)
        assert fetched.metadata == {"integration": True}
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await record_repository_service.get_record_by_id(id=entity_id)
    assert isinstance(fetched_after_commit, Record)
    assert fetched_after_commit.metadata == {"integration": True}


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    record_repository_service: RecordRepositoryAiosqliteService,
) -> None:
    connection = record_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await record_repository_service.insert_record(
        metadata={"integration": True}, transaction=connection
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await record_repository_service.get_record_by_id(id=entity_id)
    assert missing is None
