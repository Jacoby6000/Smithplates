# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.record_repository_models import (
    Branch,
    ChoiceBranch,
    Leaf,
    Record,
    SingletonChoiceLeaf,
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
    entity_id_result = await record_repository_service.create_record(
        leaves={"integration-key": Leaf(text="integration-text")},
        choices=[ChoiceBranch(branch=Branch(count=42))],
        singleton_choices=[SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))],
        groups=None,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched, Record)
    assert fetched.leaves == {"integration-key": Leaf(text="integration-text")}
    assert fetched.choices == [ChoiceBranch(branch=Branch(count=42))]
    assert fetched.singleton_choices == [SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))]
    assert fetched.groups is None

    updated = await record_repository_service.update_record(
        leaves={"integration-key": Leaf(text="integration-updated-text")},
        choices=[ChoiceBranch(branch=Branch(count=84))],
        singleton_choices=[SingletonChoiceLeaf(leaf=Leaf(text="integration-updated-text"))],
        groups=None,
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_update, Record)
    assert fetched_after_update.leaves == {"integration-key": Leaf(text="integration-updated-text")}
    assert fetched_after_update.choices == [ChoiceBranch(branch=Branch(count=84))]
    assert fetched_after_update.singleton_choices == [SingletonChoiceLeaf(leaf=Leaf(text="integration-updated-text"))]
    assert fetched_after_update.groups is None

    deleted = await record_repository_service.delete_record(id=entity_id)
    assert deleted is True

    missing = await record_repository_service.get_record(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    record_repository_service: RecordRepositoryAiosqliteService,
) -> None:
    connection = record_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await record_repository_service.create_record(
            leaves={"integration-key": Leaf(text="integration-text")},
            choices=[ChoiceBranch(branch=Branch(count=42))],
            singleton_choices=[SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))],
            groups=None,
            transaction=connection,
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await record_repository_service.get_record(id=entity_id, transaction=connection)
        assert isinstance(fetched, Record)
        assert fetched.leaves == {"integration-key": Leaf(text="integration-text")}
        assert fetched.choices == [ChoiceBranch(branch=Branch(count=42))]
        assert fetched.singleton_choices == [SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))]
        assert fetched.groups is None
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_commit, Record)
    assert fetched_after_commit.leaves == {"integration-key": Leaf(text="integration-text")}
    assert fetched_after_commit.choices == [ChoiceBranch(branch=Branch(count=42))]
    assert fetched_after_commit.singleton_choices == [SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))]
    assert fetched_after_commit.groups is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    record_repository_service: RecordRepositoryAiosqliteService,
) -> None:
    connection = record_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await record_repository_service.create_record(
        leaves={"integration-key": Leaf(text="integration-text")},
        choices=[ChoiceBranch(branch=Branch(count=42))],
        singleton_choices=[SingletonChoiceLeaf(leaf=Leaf(text="integration-text"))],
        groups=None,
        transaction=connection,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await record_repository_service.get_record(id=entity_id)
    assert missing is None
