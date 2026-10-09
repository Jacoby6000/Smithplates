"""Exercise fresh generated collection services against both database dialects."""

from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

import aiosqlite
import psycopg
import pytest
from generated.example.postgres.record_repository_psycopg import RecordRepositoryPsycopgService
from generated.example.sqlite.record_repository_aiosqlite import RecordRepositoryAiosqliteService
from testcontainers.postgres import PostgresContainer


async def exercise(service: RecordRepositoryAiosqliteService | RecordRepositoryPsycopgService) -> None:
    instant = datetime(2024, 1, 2, 3, 4, tzinfo=timezone(timedelta(hours=5, minutes=30)))
    amount = Decimal("12345678901234567890.12345678901234567890")
    payload = bytes(range(256))
    key = 'odd/"key ☃'
    identifier = await service.create_record(
        instants=[instant],
        instant_map={key: instant},
        amounts=[amount],
        amount_map={key: amount},
        payloads=[payload],
        payload_map={key: payload},
        instant_groups=[[], [instant]],
        nested_amounts={"empty": {}, key: {key: amount}},
        optional_instants=None,
    )
    row = await service.get_record(id=identifier)
    assert row is not None
    assert row.instants == [instant]
    assert row.instant_map == {key: instant}
    assert row.amounts == [amount]
    assert row.amount_map == {key: amount}
    assert row.payloads == [payload]
    assert row.payload_map == {key: payload}
    assert row.instant_groups == [[], [instant]]
    assert row.nested_amounts == {"empty": {}, key: {key: amount}}
    assert row.optional_instants is None
    for optional in ([], [instant], None):
        assert await service.update_record(
            id=identifier,
            instants=[],
            instant_map={},
            amounts=[],
            amount_map={},
            payloads=[],
            payload_map={},
            instant_groups=[],
            nested_amounts={},
            optional_instants=optional,
        )
        row = await service.get_record(id=identifier)
        assert row is not None
        assert row.optional_instants == optional
        assert row.instants == [] and row.instant_map == {}
        assert row.amounts == [] and row.amount_map == {}
        assert row.payloads == [] and row.payload_map == {}
        assert row.instant_groups == [] and row.nested_amounts == {}


@pytest.mark.asyncio
@pytest.mark.integration
@pytest.mark.sqlite
async def test_sqlite_collection_boundaries() -> None:
    async with aiosqlite.connect(":memory:") as connection:
        migration = Path(__file__).parent / "expected/db/migrations/sqlite/v1_initial_schema.sql"
        await connection.executescript(migration.read_text())
        await exercise(RecordRepositoryAiosqliteService(connection))


@pytest.mark.asyncio
@pytest.mark.integration
@pytest.mark.postgres
async def test_postgres_collection_boundaries() -> None:
    with PostgresContainer("postgres:18-alpine") as container:
        dsn = container.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")
        async with await psycopg.AsyncConnection.connect(dsn) as connection:
            migration = Path(__file__).parent / "expected/db/migrations/postgres/v1_initial_schema.sql"
            await connection.execute(migration.read_text())
            await connection.commit()
            await exercise(RecordRepositoryPsycopgService(connection))
