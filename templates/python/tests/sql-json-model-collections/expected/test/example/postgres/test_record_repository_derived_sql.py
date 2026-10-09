# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.record_repository_models import (
    Branch,
    ChoiceBranch,
    Leaf,
    Record,
)
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from generated.example.postgres.record_repository_psycopg import RecordRepositoryPsycopgService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def record_repository_service(
    postgres_dsn: str,
) -> AsyncIterator[RecordRepositoryPsycopgService]:
    connection = await psycopg.AsyncConnection.connect(postgres_dsn)
    migration_service = PsycopgMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield RecordRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(record_repository_service: RecordRepositoryPsycopgService) -> None:
    entity_id_result = await record_repository_service.create_record(
        leaves={"integration-key": Leaf(text="integration-text")},
        choices=[ChoiceBranch(branch=Branch(count=42))],
        groups=None,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched, Record)
    assert fetched.leaves == {"integration-key": Leaf(text="integration-text")}
    assert fetched.choices == [ChoiceBranch(branch=Branch(count=42))]
    assert fetched.groups is None

    updated = await record_repository_service.update_record(
        leaves={"integration-key": Leaf(text="integration-updated-text")},
        choices=[ChoiceBranch(branch=Branch(count=84))],
        groups=None,
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_update, Record)
    assert fetched_after_update.leaves == {"integration-key": Leaf(text="integration-updated-text")}
    assert fetched_after_update.choices == [ChoiceBranch(branch=Branch(count=84))]
    assert fetched_after_update.groups is None

    deleted = await record_repository_service.delete_record(id=entity_id)
    assert deleted is True

    missing = await record_repository_service.get_record(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    record_repository_service: RecordRepositoryPsycopgService,
) -> None:
    connection = record_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await record_repository_service.create_record(
            leaves={"integration-key": Leaf(text="integration-text")},
            choices=[ChoiceBranch(branch=Branch(count=42))],
            groups=None,
            transaction=tx,
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await record_repository_service.get_record(id=entity_id, transaction=tx)
        assert isinstance(fetched, Record)
        assert fetched.leaves == {"integration-key": Leaf(text="integration-text")}
        assert fetched.choices == [ChoiceBranch(branch=Branch(count=42))]
        assert fetched.groups is None

    fetched_after_commit = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_commit, Record)
    assert fetched_after_commit.leaves == {"integration-key": Leaf(text="integration-text")}
    assert fetched_after_commit.choices == [ChoiceBranch(branch=Branch(count=42))]
    assert fetched_after_commit.groups is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    record_repository_service: RecordRepositoryPsycopgService,
) -> None:
    connection = record_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await record_repository_service.create_record(
                leaves={"integration-key": Leaf(text="integration-text")},
                choices=[ChoiceBranch(branch=Branch(count=42))],
                groups=None,
                transaction=tx,
            )
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await record_repository_service.get_record(id=entity_id)
    assert missing is None
