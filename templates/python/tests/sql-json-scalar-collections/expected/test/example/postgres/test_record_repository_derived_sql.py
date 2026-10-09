# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.postgres.psycopg_migrations import PsycopgMigrationService
from generated.example.postgres.record_repository_psycopg import RecordRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def record_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[RecordRepositoryPsycopgService]:
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
        yield RecordRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(record_repository_service: RecordRepositoryPsycopgService) -> None:
    entity_id_result = await record_repository_service.create_record(
        instants=[datetime(2024, 1, 1, tzinfo=timezone.utc)],
        instant_map={"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)},
        amounts=[Decimal("3.5")],
        amount_map={"integration-key": Decimal("3.5")},
        payloads=[b"integration-payloads"],
        payload_map={"integration-key": b"integration-payload_map"},
        instant_groups=[[datetime(2024, 1, 1, tzinfo=timezone.utc)]],
        nested_amounts={"integration-key": {"integration-key": Decimal("3.5")}},
        optional_instants=None,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched, Record)
    assert fetched.instants == [datetime(2024, 1, 1, tzinfo=timezone.utc)]
    assert fetched.instant_map == {"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)}
    assert fetched.amounts == [Decimal("3.5")]
    assert fetched.amount_map == {"integration-key": Decimal("3.5")}
    assert fetched.payloads == [b"integration-payloads"]
    assert fetched.payload_map == {"integration-key": b"integration-payload_map"}
    assert fetched.instant_groups == [[datetime(2024, 1, 1, tzinfo=timezone.utc)]]
    assert fetched.nested_amounts == {"integration-key": {"integration-key": Decimal("3.5")}}
    assert fetched.optional_instants is None

    updated = await record_repository_service.update_record(
        instants=[datetime(2024, 1, 2, tzinfo=timezone.utc)],
        instant_map={"integration-key": datetime(2024, 1, 2, tzinfo=timezone.utc)},
        amounts=[Decimal("7.0")],
        amount_map={"integration-key": Decimal("7.0")},
        payloads=[b"integration-updated-payloads"],
        payload_map={"integration-key": b"integration-updated-payload_map"},
        instant_groups=[[datetime(2024, 1, 2, tzinfo=timezone.utc)]],
        nested_amounts={"integration-key": {"integration-key": Decimal("7.0")}},
        optional_instants=None,
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_update, Record)
    assert fetched_after_update.instants == [datetime(2024, 1, 2, tzinfo=timezone.utc)]
    assert fetched_after_update.instant_map == {"integration-key": datetime(2024, 1, 2, tzinfo=timezone.utc)}
    assert fetched_after_update.amounts == [Decimal("7.0")]
    assert fetched_after_update.amount_map == {"integration-key": Decimal("7.0")}
    assert fetched_after_update.payloads == [b"integration-updated-payloads"]
    assert fetched_after_update.payload_map == {"integration-key": b"integration-updated-payload_map"}
    assert fetched_after_update.instant_groups == [[datetime(2024, 1, 2, tzinfo=timezone.utc)]]
    assert fetched_after_update.nested_amounts == {"integration-key": {"integration-key": Decimal("7.0")}}
    assert fetched_after_update.optional_instants is None

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
            instants=[datetime(2024, 1, 1, tzinfo=timezone.utc)],
            instant_map={"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)},
            amounts=[Decimal("3.5")],
            amount_map={"integration-key": Decimal("3.5")},
            payloads=[b"integration-payloads"],
            payload_map={"integration-key": b"integration-payload_map"},
            instant_groups=[[datetime(2024, 1, 1, tzinfo=timezone.utc)]],
            nested_amounts={"integration-key": {"integration-key": Decimal("3.5")}},
            optional_instants=None,
            transaction=tx,
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await record_repository_service.get_record(id=entity_id, transaction=tx)
        assert isinstance(fetched, Record)
        assert fetched.instants == [datetime(2024, 1, 1, tzinfo=timezone.utc)]
        assert fetched.instant_map == {"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)}
        assert fetched.amounts == [Decimal("3.5")]
        assert fetched.amount_map == {"integration-key": Decimal("3.5")}
        assert fetched.payloads == [b"integration-payloads"]
        assert fetched.payload_map == {"integration-key": b"integration-payload_map"}
        assert fetched.instant_groups == [[datetime(2024, 1, 1, tzinfo=timezone.utc)]]
        assert fetched.nested_amounts == {"integration-key": {"integration-key": Decimal("3.5")}}
        assert fetched.optional_instants is None

    fetched_after_commit = await record_repository_service.get_record(id=entity_id)
    assert isinstance(fetched_after_commit, Record)
    assert fetched_after_commit.instants == [datetime(2024, 1, 1, tzinfo=timezone.utc)]
    assert fetched_after_commit.instant_map == {"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)}
    assert fetched_after_commit.amounts == [Decimal("3.5")]
    assert fetched_after_commit.amount_map == {"integration-key": Decimal("3.5")}
    assert fetched_after_commit.payloads == [b"integration-payloads"]
    assert fetched_after_commit.payload_map == {"integration-key": b"integration-payload_map"}
    assert fetched_after_commit.instant_groups == [[datetime(2024, 1, 1, tzinfo=timezone.utc)]]
    assert fetched_after_commit.nested_amounts == {"integration-key": {"integration-key": Decimal("3.5")}}
    assert fetched_after_commit.optional_instants is None


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
                instants=[datetime(2024, 1, 1, tzinfo=timezone.utc)],
                instant_map={"integration-key": datetime(2024, 1, 1, tzinfo=timezone.utc)},
                amounts=[Decimal("3.5")],
                amount_map={"integration-key": Decimal("3.5")},
                payloads=[b"integration-payloads"],
                payload_map={"integration-key": b"integration-payload_map"},
                instant_groups=[[datetime(2024, 1, 1, tzinfo=timezone.utc)]],
                nested_amounts={"integration-key": {"integration-key": Decimal("3.5")}},
                optional_instants=None,
                transaction=tx,
            )
            entity_id = entity_id_result
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await record_repository_service.get_record(id=entity_id)
    assert missing is None
