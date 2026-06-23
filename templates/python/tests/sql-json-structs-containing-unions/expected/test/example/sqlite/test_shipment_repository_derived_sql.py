# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.shipment_repository_models import (
    DeliveryStatePending,
    PostalAddress,
    Shipment,
)
from generated.example.sqlite.shipment_repository_aiosqlite import ShipmentRepositoryAiosqliteService
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def shipment_repository_service() -> AsyncIterator[ShipmentRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield ShipmentRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(shipment_repository_service: ShipmentRepositoryAiosqliteService) -> None:
    entity_id_result = await shipment_repository_service.create_shipment(
        label="integration-label",
        destination=PostalAddress(street="integration-street", city="integration-city"),
        state=DeliveryStatePending(pending="integration-pending"),
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched, Shipment)
    assert fetched.label == "integration-label"
    assert fetched.destination.street == "integration-street"
    assert fetched.destination.city == "integration-city"
    assert fetched.state == DeliveryStatePending(pending="integration-pending")

    updated = await shipment_repository_service.update_shipment(
        label="integration-updated-label",
        destination=PostalAddress(street="integration-updated-street", city="integration-updated-city"),
        state=DeliveryStatePending(pending="integration-updated-pending"),
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched_after_update, Shipment)
    assert fetched_after_update.label == "integration-updated-label"
    assert fetched_after_update.destination.street == "integration-updated-street"
    assert fetched_after_update.destination.city == "integration-updated-city"
    assert fetched_after_update.state == DeliveryStatePending(pending="integration-updated-pending")

    deleted = await shipment_repository_service.delete_shipment(id=entity_id)
    assert deleted is True

    missing = await shipment_repository_service.get_shipment(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    shipment_repository_service: ShipmentRepositoryAiosqliteService,
) -> None:
    connection = shipment_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await shipment_repository_service.create_shipment(
            label="integration-label",
            destination=PostalAddress(street="integration-street", city="integration-city"),
            state=DeliveryStatePending(pending="integration-pending"),
            transaction=connection,
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await shipment_repository_service.get_shipment(id=entity_id, transaction=connection)
        assert isinstance(fetched, Shipment)
        assert fetched.label == "integration-label"
        assert fetched.destination.street == "integration-street"
        assert fetched.destination.city == "integration-city"
        assert fetched.state == DeliveryStatePending(pending="integration-pending")
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched_after_commit, Shipment)
    assert fetched_after_commit.label == "integration-label"
    assert fetched_after_commit.destination.street == "integration-street"
    assert fetched_after_commit.destination.city == "integration-city"
    assert fetched_after_commit.state == DeliveryStatePending(pending="integration-pending")


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    shipment_repository_service: ShipmentRepositoryAiosqliteService,
) -> None:
    connection = shipment_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await shipment_repository_service.create_shipment(
        label="integration-label",
        destination=PostalAddress(street="integration-street", city="integration-city"),
        state=DeliveryStatePending(pending="integration-pending"),
        transaction=connection,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await shipment_repository_service.get_shipment(id=entity_id)
    assert missing is None
