# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import psycopg
import pytest
import pytest_asyncio
from testcontainers.postgres import PostgresContainer
from datetime import datetime, timezone
from widget_repository_psycopg import WidgetRepositoryPsycopgService
from widget_repository_models import (
    Widget,
    WidgetNotFound,
)

SCHEMA_DDL = """-- example#Widget
CREATE TABLE widgets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    foo TEXT,
    bar BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);
"""


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
async def widget_repository_service(
    postgres_container: PostgresContainer,
) -> AsyncIterator[WidgetRepositoryPsycopgService]:
    connection = await psycopg.AsyncConnection.connect(
        host=postgres_container.get_container_host_ip(),
        port=int(postgres_container.get_exposed_port(5432)),
        user=postgres_container.username,
        password=postgres_container.password,
        dbname=postgres_container.dbname,
    )
    await _apply_schema_ddl(connection, SCHEMA_DDL)
    try:
        yield WidgetRepositoryPsycopgService(connection)
    finally:
        await connection.close()


@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(widget_repository_service: WidgetRepositoryPsycopgService) -> None:
    entity_id = await widget_repository_service.create_widget(foo="integration-foo", bar=42)
    assert isinstance(entity_id, str)
    assert entity_id

    fetched = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched, Widget)
    assert fetched.foo == "integration-foo"
    assert fetched.bar == 42

    updated = await widget_repository_service.update_widget(foo="integration-updated-foo", bar=84, id=entity_id)
    assert updated is True

    fetched_after_update = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(fetched_after_update, Widget)
    assert fetched_after_update.foo == "integration-updated-foo"
    assert fetched_after_update.bar == 84

    deleted = await widget_repository_service.delete_widget(id=entity_id)
    assert deleted is True

    missing = await widget_repository_service.get_widget(id=entity_id)
    assert isinstance(missing, WidgetNotFound)
