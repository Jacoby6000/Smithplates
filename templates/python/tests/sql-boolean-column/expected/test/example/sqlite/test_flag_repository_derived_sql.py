# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.flag_repository_models import (
    Flag,
)
from generated.example.sqlite.flag_repository_aiosqlite import FlagRepositoryAiosqliteService
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def flag_repository_service() -> AsyncIterator[FlagRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield FlagRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(flag_repository_service: FlagRepositoryAiosqliteService) -> None:
    entity_id_result = await flag_repository_service.create_flag(label=None, enabled=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await flag_repository_service.get_flag(id=entity_id)
    assert isinstance(fetched, Flag)
    assert fetched.label is None
    assert fetched.enabled is None

    updated = await flag_repository_service.update_flag(label=None, enabled=None, id=entity_id)
    assert updated is True

    fetched_after_update = await flag_repository_service.get_flag(id=entity_id)
    assert isinstance(fetched_after_update, Flag)
    assert fetched_after_update.label is None
    assert fetched_after_update.enabled is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(flag_repository_service: FlagRepositoryAiosqliteService) -> None:
    connection = flag_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await flag_repository_service.create_flag(label=None, enabled=None, transaction=connection)
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await flag_repository_service.get_flag(id=entity_id, transaction=connection)
        assert isinstance(fetched, Flag)
        assert fetched.label is None
        assert fetched.enabled is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await flag_repository_service.get_flag(id=entity_id)
    assert isinstance(fetched_after_commit, Flag)
    assert fetched_after_commit.label is None
    assert fetched_after_commit.enabled is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    flag_repository_service: FlagRepositoryAiosqliteService,
) -> None:
    connection = flag_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await flag_repository_service.create_flag(label=None, enabled=None, transaction=connection)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await flag_repository_service.get_flag(id=entity_id)
    assert missing is None
