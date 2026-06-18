# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from datetime import datetime
from typing import cast, override

import psycopg
from generated.db.models.widget_repository_models import (
    CreateWidgetResult,
    DeleteWidgetOutput,
    UpdateWidgetResult,
    Widget,
)
from generated.db.postgres.psycopg_transaction_run import run
from generated.db.widget_repository_protocol import WidgetRepositoryServiceProtocol
from psycopg.rows import class_row, dict_row
from psycopg.types.string import TextLoader


class WidgetRepositoryPsycopgService(WidgetRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def create_widget(
        self,
        foo: str | None,
        bar: int | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> CreateWidgetResult:
        async def execute() -> CreateWidgetResult:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """INSERT INTO widgets (foo, bar) VALUES (%s, %s) RETURNING id, created_at, updated_at;""",
                    (foo, bar),
                )
                row = await cur.fetchone()
                if row is None:
                    raise RuntimeError("INSERT RETURNING produced no row")
                return CreateWidgetResult(
                    id=_read_str_col(row, "id"),
                    created_at=_read_datetime_col(row, "created_at"),
                    updated_at=_read_datetime_col(row, "updated_at"),
                )

        return await run(self._connection, transaction, execute)

    @override
    async def get_widget(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Widget | None:
        async def execute() -> Widget | None:
            async with self._connection.cursor(row_factory=class_row(Widget)) as cur:
                await cur.execute(
                    """SELECT widgets.id, widgets.foo, widgets.bar, widgets.created_at, widgets.updated_at
FROM widgets
WHERE id = %s;""",
                    (id,),
                )
                return await cur.fetchone()

        return await run(self._connection, transaction, execute)

    @override
    async def update_widget(
        self,
        foo: str | None,
        bar: int | None,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> UpdateWidgetResult:
        async def execute() -> UpdateWidgetResult:
            cur = await self._connection.execute(
                """UPDATE widgets
SET foo = %s, bar = %s, updated_at = CURRENT_TIMESTAMP
WHERE id = %s RETURNING id, created_at, updated_at;""",
                (foo, bar, id),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return UpdateWidgetResult(
                id=_read_str(row, 0),
                created_at=_read_datetime(row, 1),
                updated_at=_read_datetime(row, 2),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def delete_widget(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> DeleteWidgetOutput:
        async def execute() -> DeleteWidgetOutput:
            cur = await self._connection.execute(
                """DELETE FROM widgets WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
            return DeleteWidgetOutput(deleted=row is not None)

        return await run(self._connection, transaction, execute)


def _read_datetime(row: tuple[object, ...], index: int) -> datetime:
    return cast(datetime, row[index])


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_datetime_col(row: dict[str, object], column: str) -> datetime:
    return cast(datetime, row[column])


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
