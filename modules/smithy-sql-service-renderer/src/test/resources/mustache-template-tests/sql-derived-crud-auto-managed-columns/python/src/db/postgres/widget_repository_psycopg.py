# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

from typing import cast
import uuid
from datetime import datetime
import psycopg

from widget_repository_models import (
    Widget,
)
from widget_repository_protocol import WidgetRepositoryServiceProtocol


class WidgetRepositoryPsycopgService(WidgetRepositoryServiceProtocol):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_widget(
        self,
        foo: str,
        bar: int,
    ) -> str:
        cur = await self._connection.execute(
            """INSERT INTO widgets (foo, bar) VALUES (%s, %s) RETURNING id;""",
            (foo, bar),
        )
        row = await cur.fetchone()
        if row is None:
            raise RuntimeError("INSERT RETURNING produced no row")
        return _read_str(row, 0)
    @override
    async def get_widget(
        self,
        id: str,
    ) -> Widget | None:
        cur = await self._connection.execute(
            """SELECT id, foo, bar, created_at, updated_at FROM widgets WHERE id = %s;""",
            (id,),
        )
        row = await cur.fetchone()
        if row is None:
            return None
        return Widget(
            id=_read_str(row, 0),
            foo=_read_str(row, 1),
            bar=_read_int(row, 2),
            created_at=_read_datetime(row, 3),
            updated_at=_read_datetime(row, 4),
        )
    @override
    async def update_widget(
        self,
        foo: str,
        bar: int,
        id: str,
    ) -> bool:
        cur = await self._connection.execute(
            """UPDATE widgets
SET foo = %s, bar = %s, updated_at = CURRENT_TIMESTAMP
WHERE id = %s RETURNING updated_at;""",
            (foo, bar, id),
        )
        row = await cur.fetchone()
        return row is not None
    @override
    async def delete_widget(
        self,
        id: str,
    ) -> bool:
        cur = await self._connection.execute(
            """DELETE FROM widgets WHERE id = %s RETURNING id;""",
            (id,),
        )
        row = await cur.fetchone()
        return row is not None
def _read_datetime(row: tuple[object, ...], index: int) -> datetime:
    return cast(datetime, row[index])

def _read_int(row: tuple[object, ...], index: int) -> int:
    return cast(int, row[index])

def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
