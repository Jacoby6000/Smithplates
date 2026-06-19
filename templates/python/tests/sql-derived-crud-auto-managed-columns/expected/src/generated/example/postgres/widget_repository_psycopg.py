# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.models.widget_repository_models import (
    Widget,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.widget_repository_protocol import WidgetRepositoryServiceProtocol
from psycopg.rows import class_row
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
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO widgets (foo, bar) VALUES (%s, %s) RETURNING id;""",
                (foo, bar),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

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
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE widgets
SET foo = %s, bar = %s, updated_at = CURRENT_TIMESTAMP
WHERE id = %s RETURNING updated_at;""",
                (foo, bar, id),
            )
            row = await cur.fetchone()
            return row is not None

        return await run(self._connection, transaction, execute)

    @override
    async def delete_widget(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """DELETE FROM widgets WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
            return row is not None

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
