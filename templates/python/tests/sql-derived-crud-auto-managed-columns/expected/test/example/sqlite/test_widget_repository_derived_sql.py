# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService
from generated.example.sqlite.widget_repository_aiosqlite import WidgetRepositoryAiosqliteService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def widget_repository_service() -> AsyncIterator[WidgetRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield WidgetRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(widget_repository_service: WidgetRepositoryAiosqliteService) -> None:
    entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched, dict)
    assert fetched["foo"] is None
    assert fetched["bar"] is None

    updated = await widget_repository_service.update_widget(foo=None, bar=None, id=entity_id)
    assert updated is True

    fetched_after_update = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched_after_update, dict)
    assert fetched_after_update["foo"] is None
    assert fetched_after_update["bar"] is None

    deleted = await widget_repository_service.delete_widget(id=entity_id)
    assert deleted is True

    missing = await widget_repository_service.get_widget(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    widget_repository_service: WidgetRepositoryAiosqliteService,
) -> None:
    connection = widget_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None, transaction=connection)
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await widget_repository_service.get_widget(id=entity_id, transaction=connection)
        assert isinstance(fetched, dict)
        assert fetched["foo"] is None
        assert fetched["bar"] is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched_after_commit, dict)
    assert fetched_after_commit["foo"] is None
    assert fetched_after_commit["bar"] is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    widget_repository_service: WidgetRepositoryAiosqliteService,
) -> None:
    connection = widget_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None, transaction=connection)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await widget_repository_service.get_widget(id=entity_id)
    assert missing is None
