# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
import aiosqlite
from datetime import datetime, timezone
from widget_repository_aiosqlite import WidgetRepositoryAiosqliteService
from widget_repository_models import (
    Widget,
    WidgetNotFound,
)

SCHEMA_DDL = """-- example#Widget
CREATE TABLE widgets (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    foo TEXT,
    bar BIGINT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);
"""


@pytest_asyncio.fixture
async def widget_repository_service() -> AsyncIterator[WidgetRepositoryAiosqliteService]:
    connection = await aiosqlite.connect(":memory:")
    await connection.executescript(SCHEMA_DDL)
    await connection.commit()
    try:
        yield WidgetRepositoryAiosqliteService(connection)
    finally:
        await connection.close()


@pytest.mark.asyncio
async def test_derived_sql_methods_lifecycle(widget_repository_service: WidgetRepositoryAiosqliteService) -> None:
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
