# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
import aiosqlite
from datetime import datetime, timezone
from shipment_repository_aiosqlite import ShipmentRepositoryAiosqliteService
from shipment_repository_models import (
    PostalAddress,
    Shipment,
    ShipmentNotFound,
    DeliveryState,
)

SCHEMA_DDL = """-- example#Shipment
CREATE TABLE shipments (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT,
    destination TEXT,
    state TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);
"""


@pytest_asyncio.fixture
async def shipment_repository_service() -> AsyncIterator[ShipmentRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    await connection.executescript(SCHEMA_DDL)
    await connection.commit()
    try:
        yield ShipmentRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(shipment_repository_service: ShipmentRepositoryAiosqliteService) -> None:
    entity_id = await shipment_repository_service.create_shipment(label="integration-label", destination=PostalAddress(street="integration-street", city="integration-city"), state={"pending": "integration-pending"})
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await shipment_repository_service.get_shipment(id=entity_id)
    assert isinstance(fetched, Shipment)
    assert fetched.label == "integration-label"
    assert fetched.destination.street == "integration-street"
    assert fetched.destination.city == "integration-city"
    assert fetched.state == {"pending": "integration-pending"}

    updated = await shipment_repository_service.update_shipment(label="integration-updated-label", destination=PostalAddress(street="integration-updated-street", city="integration-updated-city"), state={"pending": "integration-updated-pending"}, id=entity_id)
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
    assert isinstance(missing, ShipmentNotFound)
