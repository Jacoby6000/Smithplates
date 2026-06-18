# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.db.models.widget_repository_models import (
    Widget,
)
from generated.db.postgres.psycopg_migrations import PsycopgMigrationService
from generated.db.postgres.widget_repository_psycopg import WidgetRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def widget_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[WidgetRepositoryPsycopgService]:
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
        yield WidgetRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(widget_repository_service: WidgetRepositoryPsycopgService) -> None:
    entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched, Widget)
    assert fetched.foo is None
    assert fetched.bar is None

    updated = await widget_repository_service.update_widget(foo=None, bar=None, id=entity_id)
    assert updated is True

    fetched_after_update = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched_after_update, Widget)
    assert fetched_after_update.foo is None
    assert fetched_after_update.bar is None

    deleted = await widget_repository_service.delete_widget(id=entity_id)
    assert deleted is True

    missing = await widget_repository_service.get_widget(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    widget_repository_service: WidgetRepositoryPsycopgService,
) -> None:
    connection = widget_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None, transaction=tx)
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await widget_repository_service.get_widget(id=entity_id, transaction=tx)
        assert isinstance(fetched, Widget)
        assert fetched.foo is None
        assert fetched.bar is None

    fetched_after_commit = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched_after_commit, Widget)
    assert fetched_after_commit.foo is None
    assert fetched_after_commit.bar is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    widget_repository_service: WidgetRepositoryPsycopgService,
) -> None:
    connection = widget_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await widget_repository_service.create_widget(foo=None, bar=None, transaction=tx)
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await widget_repository_service.get_widget(id=entity_id)
    assert missing is None
