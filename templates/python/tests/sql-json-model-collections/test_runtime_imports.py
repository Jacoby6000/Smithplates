"""Collection-only models must retain runtime helper and annotation imports."""

import json
from pathlib import Path
from typing import get_type_hints

import aiosqlite
import psycopg
import pytest
from generated.example.models.record_repository_models import (
    Branch,
    Choice,
    ChoiceBranch,
    Leaf,
    SingletonChoice,
    SingletonChoiceLeaf,
)
from generated.example.postgres import record_repository_psycopg as postgres
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from generated.example.sqlite import record_repository_aiosqlite as sqlite
from testcontainers.postgres import PostgresContainer


async def exercise(
    service: sqlite.RecordRepositoryAiosqliteService | postgres.RecordRepositoryPsycopgService,
) -> None:
    hints = get_type_hints(RecordRepositoryServiceProtocol.create_record)
    assert hints["leaves"] == dict[str, Leaf]
    assert hints["choices"] == list[Choice]
    assert hints["singleton_choices"] == list[SingletonChoice]
    assert hints["groups"] == dict[str, list[Branch]] | None
    leaves = {'odd/"key ☃': Leaf(text="leaf")}
    choices: list[Choice] = [ChoiceBranch(branch=Branch(count=42))]
    groups = {"empty": [], "branches": [Branch(count=7)]}
    singleton_choices: list[SingletonChoice] = [SingletonChoiceLeaf(leaf=Leaf(text="singleton"))]
    identifier = await service.create_record(
        leaves=leaves, choices=choices, singleton_choices=singleton_choices, groups=None
    )
    for value in (groups, {}, None):
        assert await service.update_record(
            id=identifier,
            leaves=leaves,
            choices=choices,
            singleton_choices=singleton_choices,
            groups=value,
        )
        row = await service.get_record(id=identifier)
        assert row is not None
        assert row.leaves == leaves
        assert row.choices == choices
        assert row.singleton_choices == singleton_choices
        assert row.groups == value
    assert await service.update_record(id=identifier, leaves=leaves, choices=choices, singleton_choices=[], groups=None)
    row = await service.get_record(id=identifier)
    assert row is not None
    assert row.singleton_choices == []


@pytest.mark.asyncio
@pytest.mark.integration
@pytest.mark.sqlite
async def test_sqlite_collection_imports() -> None:
    assert json.loads(sqlite._json_bind_Leaf(Leaf(text="leaf"))) == {"text": "leaf"}
    assert json.loads(sqlite._json_bind_Choice(ChoiceBranch(branch=Branch(count=42)))) == {"branch": {"count": 42}}
    async with aiosqlite.connect(":memory:") as connection:
        migration = Path(__file__).parent / "expected/db/migrations/sqlite/v1_initial_schema.sql"
        await connection.executescript(migration.read_text())
        await exercise(sqlite.RecordRepositoryAiosqliteService(connection))


@pytest.mark.asyncio
@pytest.mark.integration
@pytest.mark.postgres
async def test_postgres_collection_imports() -> None:
    assert json.loads(postgres._json_bind_Leaf(Leaf(text="leaf"))) == {"text": "leaf"}
    assert json.loads(postgres._json_bind_Choice(ChoiceBranch(branch=Branch(count=42)))) == {"branch": {"count": 42}}
    with PostgresContainer("postgres:18-alpine") as container:
        dsn = container.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")
        async with await psycopg.AsyncConnection.connect(dsn) as connection:
            migration = Path(__file__).parent / "expected/db/migrations/postgres/v1_initial_schema.sql"
            await connection.execute(migration.read_text())
            await connection.commit()
            await exercise(postgres.RecordRepositoryPsycopgService(connection))
