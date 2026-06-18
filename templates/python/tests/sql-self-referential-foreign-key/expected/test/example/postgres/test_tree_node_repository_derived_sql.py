# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.tree_node_repository_models import (
    TreeNode,
)
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from generated.example.postgres.tree_node_repository_psycopg import TreeNodeRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def tree_node_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[TreeNodeRepositoryPsycopgService]:
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
        yield TreeNodeRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(tree_node_repository_service: TreeNodeRepositoryPsycopgService) -> None:
    entity_id_result = await tree_node_repository_service.create_tree_node(label=None, parent_node_id=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert isinstance(fetched, TreeNode)
    assert fetched.label is None
    assert fetched.parent_node_id is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    tree_node_repository_service: TreeNodeRepositoryPsycopgService,
) -> None:
    connection = tree_node_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await tree_node_repository_service.create_tree_node(
            label=None, parent_node_id=None, transaction=tx
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await tree_node_repository_service.get_tree_node(id=entity_id, transaction=tx)
        assert isinstance(fetched, TreeNode)
        assert fetched.label is None
        assert fetched.parent_node_id is None

    fetched_after_commit = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert isinstance(fetched_after_commit, TreeNode)
    assert fetched_after_commit.label is None
    assert fetched_after_commit.parent_node_id is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    tree_node_repository_service: TreeNodeRepositoryPsycopgService,
) -> None:
    connection = tree_node_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await tree_node_repository_service.create_tree_node(
                label=None, parent_node_id=None, transaction=tx
            )
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert missing is None
