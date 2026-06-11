# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from psycopg_migrations import PsycopgMigrationService
from shipment_repository_models import (
    PostalAddress,
    Shipment,
)
from shipment_repository_psycopg import ShipmentRepositoryPsycopgService
from testcontainers.postgres import PostgresContainer

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def shipment_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[ShipmentRepositoryPsycopgService]:
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
        yield ShipmentRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(shipment_repository_service: ShipmentRepositoryPsycopgService) -> None:
    entity_id = await shipment_repository_service.create_shipment(
        label="integration-label",
        destination=PostalAddress(street="integration-street", city="integration-city"),
        state={"pending": "integration-pending"},
    )
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched, Shipment)
    assert fetched.label == "integration-label"
    assert fetched.destination.street == "integration-street"
    assert fetched.destination.city == "integration-city"
    assert fetched.state == {"pending": "integration-pending"}

    updated = await shipment_repository_service.update_shipment(
        label="integration-updated-label",
        destination=PostalAddress(street="integration-updated-street", city="integration-updated-city"),
        state={"pending": "integration-updated-pending"},
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched_after_update, Shipment)
    assert fetched_after_update.label == "integration-updated-label"
    assert fetched_after_update.destination.street == "integration-updated-street"
    assert fetched_after_update.destination.city == "integration-updated-city"
    assert fetched_after_update.state == {"pending": "integration-updated-pending"}

    deleted = await shipment_repository_service.delete_shipment(id=entity_id)
    assert deleted is True

    missing = await shipment_repository_service.get_shipment(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    shipment_repository_service: ShipmentRepositoryPsycopgService,
) -> None:
    connection = shipment_repository_service._connection
    async with connection.transaction() as tx:
        entity_id = await shipment_repository_service.create_shipment(
            label="integration-label",
            destination=PostalAddress(street="integration-street", city="integration-city"),
            state={"pending": "integration-pending"},
            transaction=tx,
        )
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await shipment_repository_service.get_shipment(id=entity_id, transaction=tx)
        assert isinstance(fetched, Shipment)
        assert fetched.label == "integration-label"
        assert fetched.destination.street == "integration-street"
        assert fetched.destination.city == "integration-city"
        assert fetched.state == {"pending": "integration-pending"}

    fetched_after_commit = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched_after_commit, Shipment)
    assert fetched_after_commit.label == "integration-label"
    assert fetched_after_commit.destination.street == "integration-street"
    assert fetched_after_commit.destination.city == "integration-city"
    assert fetched_after_commit.state == {"pending": "integration-pending"}


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    shipment_repository_service: ShipmentRepositoryPsycopgService,
) -> None:
    connection = shipment_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id = await shipment_repository_service.create_shipment(
                label="integration-label",
                destination=PostalAddress(street="integration-street", city="integration-city"),
                state={"pending": "integration-pending"},
                transaction=tx,
            )
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await shipment_repository_service.get_shipment(id=entity_id)
    assert missing is None
