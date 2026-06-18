# Generated from petstore.db#PetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import psycopg
import pytest
import pytest_asyncio
from testcontainers.postgres import PostgresContainer

from generated.petstore.db.models.pet_repository_models import (
    PetHighlight,
    PetTags,
)
from generated.petstore.db.pet_repository_protocol import (
    GetPetRecordResult,
)
from generated.petstore.db.postgres.pet_repository_psycopg import PetRepositoryPsycopgService
from generated.petstore.db.postgres.psycopg_migrations import PsycopgMigrationService

MIGRATIONS_DIRECTORY = Path(__file__).resolve().parents[3] / "db" / "migrations" / "postgres"


@pytest_asyncio.fixture
async def pet_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[PetRepositoryPsycopgService]:
    connection = await psycopg.AsyncConnection.connect(
        host=postgres_container.get_container_host_ip(),
        port=int(postgres_container.get_exposed_port(5432)),
        user=postgres_container.username,
        password=postgres_container.password,
        dbname=postgres_container.dbname,
    )
    migration_service = PsycopgMigrationService(connection, migrations_directory=MIGRATIONS_DIRECTORY)
    await migration_service.migrate_all()
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d511c78e-cf3b-3fd2-9037-db356d2b78f1', 'integration-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.execute(
        """INSERT INTO categories (id, name, store_id) VALUES ('783e2af0-df1e-3d3d-aa50-3d085860a405', 'integration-name', 'd511c78e-cf3b-3fd2-9037-db356d2b78f1') ON CONFLICT DO NOTHING;"""
    )
    await connection.execute(
        """INSERT INTO stores (id, name) VALUES ('d743e19a-1ec3-375f-ab9d-c89e5d0fc587', 'integration-updated-name') ON CONFLICT DO NOTHING;"""
    )
    await connection.execute(
        """INSERT INTO categories (id, name, store_id) VALUES ('068b7b16-6db9-35b9-8a2b-093326b15812', 'integration-updated-name', 'd743e19a-1ec3-375f-ab9d-c89e5d0fc587') ON CONFLICT DO NOTHING;"""
    )
    await connection.commit()
    try:
        yield PetRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(pet_repository_service: PetRepositoryPsycopgService) -> None:
    entity_id_result = await pet_repository_service.create_pet_record(
        name="integration-name",
        status="available",
        species=3,
        category_id="783e2af0-df1e-3d3d-aa50-3d085860a405",
        owner_id=None,
        tag_count=42,
        tags=PetTags(items=["integration-items"]),
        featured_attribute=PetHighlight(name="integration-name", color="integration-color"),
        photo=None,
        adopted_at=None,
    )
    entity_id = entity_id_result.id
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await pet_repository_service.get_pet_record(id=entity_id)
    assert isinstance(fetched, GetPetRecordResult)
    assert fetched.name == "integration-name"
    assert fetched.status == "available"
    assert fetched.species == 3
    assert fetched.category_id == "783e2af0-df1e-3d3d-aa50-3d085860a405"
    assert fetched.owner_id is None
    assert fetched.tag_count == 42
    assert fetched.tags.items == ["integration-items"]
    assert fetched.featured_attribute.name == "integration-name"
    assert fetched.featured_attribute.color == "integration-color"
    assert fetched.photo is None
    assert fetched.adopted_at is None

    updated = await pet_repository_service.update_pet_record(
        name="integration-updated-name",
        status="available",
        species=3,
        category_id="068b7b16-6db9-35b9-8a2b-093326b15812",
        owner_id=None,
        tag_count=84,
        tags=PetTags(items=["integration-updated-items"]),
        featured_attribute=PetHighlight(name="integration-updated-name", color="integration-updated-color"),
        photo=None,
        adopted_at=None,
        id=entity_id,
    )
    assert updated.updated is True

    fetched_after_update = await pet_repository_service.get_pet_record(id=entity_id)
    assert isinstance(fetched_after_update, GetPetRecordResult)
    assert fetched_after_update.name == "integration-updated-name"
    assert fetched_after_update.status == "available"
    assert fetched_after_update.species == 3
    assert fetched_after_update.category_id == "068b7b16-6db9-35b9-8a2b-093326b15812"
    assert fetched_after_update.owner_id is None
    assert fetched_after_update.tag_count == 84
    assert fetched_after_update.tags.items == ["integration-updated-items"]
    assert fetched_after_update.featured_attribute.name == "integration-updated-name"
    assert fetched_after_update.featured_attribute.color == "integration-updated-color"
    assert fetched_after_update.photo is None
    assert fetched_after_update.adopted_at is None

    deleted = await pet_repository_service.delete_pet_record(id=entity_id)
    assert deleted.deleted is True

    missing = await pet_repository_service.get_pet_record(id=entity_id)
    assert missing is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit(pet_repository_service: PetRepositoryPsycopgService) -> None:
    connection = pet_repository_service._connection
    async with connection.transaction() as tx:
        entity_id_result = await pet_repository_service.create_pet_record(
            name="integration-name",
            status="available",
            species=3,
            category_id="783e2af0-df1e-3d3d-aa50-3d085860a405",
            owner_id=None,
            tag_count=42,
            tags=PetTags(items=["integration-items"]),
            featured_attribute=PetHighlight(name="integration-name", color="integration-color"),
            photo=None,
            adopted_at=None,
            transaction=tx,
        )
        entity_id = entity_id_result.id
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await pet_repository_service.get_pet_record(id=entity_id, transaction=tx)
        assert isinstance(fetched, GetPetRecordResult)
        assert fetched.name == "integration-name"
        assert fetched.status == "available"
        assert fetched.species == 3
        assert fetched.category_id == "783e2af0-df1e-3d3d-aa50-3d085860a405"
        assert fetched.owner_id is None
        assert fetched.tag_count == 42
        assert fetched.tags.items == ["integration-items"]
        assert fetched.featured_attribute.name == "integration-name"
        assert fetched.featured_attribute.color == "integration-color"
        assert fetched.photo is None
        assert fetched.adopted_at is None

    fetched_after_commit = await pet_repository_service.get_pet_record(id=entity_id)
    assert isinstance(fetched_after_commit, GetPetRecordResult)
    assert fetched_after_commit.name == "integration-name"
    assert fetched_after_commit.status == "available"
    assert fetched_after_commit.species == 3
    assert fetched_after_commit.category_id == "783e2af0-df1e-3d3d-aa50-3d085860a405"
    assert fetched_after_commit.owner_id is None
    assert fetched_after_commit.tag_count == 42
    assert fetched_after_commit.tags.items == ["integration-items"]
    assert fetched_after_commit.featured_attribute.name == "integration-name"
    assert fetched_after_commit.featured_attribute.color == "integration-color"
    assert fetched_after_commit.photo is None
    assert fetched_after_commit.adopted_at is None


@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback(pet_repository_service: PetRepositoryPsycopgService) -> None:
    connection = pet_repository_service._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id_result = await pet_repository_service.create_pet_record(
                name="integration-name",
                status="available",
                species=3,
                category_id="783e2af0-df1e-3d3d-aa50-3d085860a405",
                owner_id=None,
                tag_count=42,
                tags=PetTags(items=["integration-items"]),
                featured_attribute=PetHighlight(name="integration-name", color="integration-color"),
                photo=None,
                adopted_at=None,
                transaction=tx,
            )
            entity_id = entity_id_result.id
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await pet_repository_service.get_pet_record(id=entity_id)
    assert missing is None
