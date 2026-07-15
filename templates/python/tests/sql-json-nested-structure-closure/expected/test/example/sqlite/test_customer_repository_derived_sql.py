# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import datetime, timezone
from pathlib import Path

import aiosqlite
import pytest
import pytest_asyncio
from generated.example.models.customer_repository_models import (
    ContactInfo,
    Customer,
    GeoCoordinates,
    PostalAddress,
)
from generated.example.sqlite.customer_repository_aiosqlite import CustomerRepositoryAiosqliteService
from generated.example.sqlite.sqlite_migrations import SqliteMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "sqlite"


@pytest_asyncio.fixture
async def customer_repository_service() -> AsyncIterator[CustomerRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    migration_service = SqliteMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.commit()
    try:
        yield CustomerRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(customer_repository_service: CustomerRepositoryAiosqliteService) -> None:
    entity_id_result = await customer_repository_service.create_customer(
        name="integration-name",
        contact=ContactInfo(
            email="integration-email",
            address=PostalAddress(
                street="integration-street",
                city="integration-city",
                coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
            ),
        ),
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await customer_repository_service.get_customer(id=entity_id)
    assert isinstance(fetched, Customer)
    assert fetched.name == "integration-name"
    assert fetched.contact.email == "integration-email"
    assert fetched.contact.address == PostalAddress(
        street="integration-street",
        city="integration-city",
        coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
    )

    updated = await customer_repository_service.update_customer(
        name="integration-updated-name",
        contact=ContactInfo(
            email="integration-updated-email",
            address=PostalAddress(
                street="integration-updated-street",
                city="integration-updated-city",
                coords=GeoCoordinates(lat=7.0, lng=7.0, recorded_at=datetime(2024, 1, 2, tzinfo=timezone.utc)),
            ),
        ),
        id=entity_id,
    )
    assert updated is True

    fetched_after_update = await customer_repository_service.get_customer(id=entity_id)
    assert isinstance(fetched_after_update, Customer)
    assert fetched_after_update.name == "integration-updated-name"
    assert fetched_after_update.contact.email == "integration-updated-email"
    assert fetched_after_update.contact.address == PostalAddress(
        street="integration-updated-street",
        city="integration-updated-city",
        coords=GeoCoordinates(lat=7.0, lng=7.0, recorded_at=datetime(2024, 1, 2, tzinfo=timezone.utc)),
    )

    deleted = await customer_repository_service.delete_customer(id=entity_id)
    assert deleted is True

    missing = await customer_repository_service.get_customer(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(
    customer_repository_service: CustomerRepositoryAiosqliteService,
) -> None:
    connection = customer_repository_service._connection
    await connection.execute("BEGIN")
    try:
        entity_id_result = await customer_repository_service.create_customer(
            name="integration-name",
            contact=ContactInfo(
                email="integration-email",
                address=PostalAddress(
                    street="integration-street",
                    city="integration-city",
                    coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
                ),
            ),
            transaction=connection,
        )
        entity_id = entity_id_result
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await customer_repository_service.get_customer(id=entity_id, transaction=connection)
        assert isinstance(fetched, Customer)
        assert fetched.name == "integration-name"
        assert fetched.contact.email == "integration-email"
        assert fetched.contact.address == PostalAddress(
            street="integration-street",
            city="integration-city",
            coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
        )
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await customer_repository_service.get_customer(id=entity_id)
    assert isinstance(fetched_after_commit, Customer)
    assert fetched_after_commit.name == "integration-name"
    assert fetched_after_commit.contact.email == "integration-email"
    assert fetched_after_commit.contact.address == PostalAddress(
        street="integration-street",
        city="integration-city",
        coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
    )


@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(
    customer_repository_service: CustomerRepositoryAiosqliteService,
) -> None:
    connection = customer_repository_service._connection
    await connection.execute("BEGIN")
    entity_id_result = await customer_repository_service.create_customer(
        name="integration-name",
        contact=ContactInfo(
            email="integration-email",
            address=PostalAddress(
                street="integration-street",
                city="integration-city",
                coords=GeoCoordinates(lat=3.5, lng=3.5, recorded_at=datetime(2024, 1, 1, tzinfo=timezone.utc)),
            ),
        ),
        transaction=connection,
    )
    entity_id = entity_id_result
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await customer_repository_service.get_customer(id=entity_id)
    assert missing is None
