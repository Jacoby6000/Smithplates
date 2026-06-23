# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService
from generated.example.sqlite.tree_node_repository_aiosqlite import TreeNodeRepositoryAiosqliteService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def tree_node_repository_service() -> AsyncIterator[TreeNodeRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield TreeNodeRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(tree_node_repository_service: TreeNodeRepositoryAiosqliteService) -> None:
    entity_id_result = await tree_node_repository_service.create_tree_node(label=None, parent_node_id=None)
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert isinstance(fetched, dict)
    assert fetched["label"] is None
    assert fetched["parent_node_id"] is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    tree_node_repository_service: TreeNodeRepositoryAiosqliteService,
) -> None:
    connection = tree_node_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await tree_node_repository_service.create_tree_node(
            label=None, parent_node_id=None, transaction=connection
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await tree_node_repository_service.get_tree_node(id=entity_id, transaction=connection)
        assert isinstance(fetched, dict)
        assert fetched["label"] is None
        assert fetched["parent_node_id"] is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert isinstance(fetched_after_commit, dict)
    assert fetched_after_commit["label"] is None
    assert fetched_after_commit["parent_node_id"] is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    tree_node_repository_service: TreeNodeRepositoryAiosqliteService,
) -> None:
    connection = tree_node_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await tree_node_repository_service.create_tree_node(
        label=None, parent_node_id=None, transaction=connection
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await tree_node_repository_service.get_tree_node(id=entity_id)
    assert missing is None
