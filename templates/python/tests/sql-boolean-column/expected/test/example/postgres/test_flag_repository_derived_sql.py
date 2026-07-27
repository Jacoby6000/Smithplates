# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.flag_repository_models import (
    Flag,
)
from generated.example.postgres.flag_repository_psycopg import FlagRepositoryPsycopgService
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def flag_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[FlagRepositoryPsycopgService]:
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
        yield FlagRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(flag_repository_service: FlagRepositoryPsycopgService) -> None:
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
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(flag_repository_service: FlagRepositoryPsycopgService) -> None:
    connection = flag_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await flag_repository_service.create_flag(label=None, enabled=None, transaction=tx)
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await flag_repository_service.get_flag(id=entity_id, transaction=tx)
        assert isinstance(fetched, Flag)
        assert fetched.label is None
        assert fetched.enabled is None

    fetched_after_commit = await flag_repository_service.get_flag(id=entity_id)
    assert isinstance(fetched_after_commit, Flag)
    assert fetched_after_commit.label is None
    assert fetched_after_commit.enabled is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(flag_repository_service: FlagRepositoryPsycopgService) -> None:
    connection = flag_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await flag_repository_service.create_flag(label=None, enabled=None, transaction=tx)
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await flag_repository_service.get_flag(id=entity_id)
    assert missing is None
