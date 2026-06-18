# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.db.models.category_repository_models import (
    Category,
    CreateCategoryResult,
)
from generated.db.postgres.category_repository_psycopg import CategoryRepositoryPsycopgService
from generated.db.postgres.psycopg_migrations import PsycopgMigrationService
from testcontainers.postgres import PostgresContainer

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
    await connection.commit()
    try:
        yield CategoryRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(category_repository_service: CategoryRepositoryPsycopgService) -> None:
    created = await category_repository_service.create_category(name=None, parent_category_id=None)
    assert isinstance(created, CreateCategoryResult)
    entity_id = created.id
    assert entity_id

    fetched = await category_repository_service.get_category(id=entity_id)
    assert isinstance(fetched, Category)
    assert fetched.name is None
    assert fetched.parent_category_id is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    category_repository_service: CategoryRepositoryPsycopgService,
) -> None:
    connection = category_repository_service._connection
    async with connection.transaction() as tx:
        created = await category_repository_service.create_category(name=None, parent_category_id=None, transaction=tx)
        assert isinstance(created, CreateCategoryResult)
        entity_id = created.id
        assert entity_id

        fetched = await category_repository_service.get_category(id=entity_id, transaction=tx)
        assert isinstance(fetched, Category)
        assert fetched.name is None
        assert fetched.parent_category_id is None

    fetched_after_commit = await category_repository_service.get_category(id=entity_id)
    assert isinstance(fetched_after_commit, Category)
    assert fetched_after_commit.name is None
    assert fetched_after_commit.parent_category_id is None


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
            created = await category_repository_service.create_category(
                name=None, parent_category_id=None, transaction=tx
            )
            entity_id = created.id
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await category_repository_service.get_category(id=entity_id)
    assert missing is None
