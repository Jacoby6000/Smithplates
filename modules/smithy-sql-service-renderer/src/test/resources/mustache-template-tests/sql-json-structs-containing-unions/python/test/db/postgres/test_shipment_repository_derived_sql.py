# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import psycopg
import pytest
import pytest_asyncio
from testcontainers.postgres import PostgresContainer
from datetime import datetime, timezone
from shipment_repository_psycopg import ShipmentRepositoryPsycopgService
from shipment_repository_models import (
    PostalAddress,
    Shipment,
    ShipmentNotFound,
    DeliveryState,
)

SCHEMA_DDL = """-- example#Shipment
CREATE TABLE shipments (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    destination JSONB,
    state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);"""


async def _apply_schema_ddl(connection: psycopg.AsyncConnection, schema_ddl: str) -> None:
    for statement in schema_ddl.split(";"):
        ddl_statement = statement.strip()
        if ddl_statement:
            _ = await connection.execute(f"{ddl_statement};")


@pytest.fixture(scope="session")
def postgres_container() -> Iterator[PostgresContainer]:
    with PostgresContainer("postgres:16-alpine") as container:
        yield container


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
    await _apply_schema_ddl(connection, SCHEMA_DDL)
    try:
        yield ShipmentRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(shipment_repository_service: ShipmentRepositoryPsycopgService) -> None:
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
