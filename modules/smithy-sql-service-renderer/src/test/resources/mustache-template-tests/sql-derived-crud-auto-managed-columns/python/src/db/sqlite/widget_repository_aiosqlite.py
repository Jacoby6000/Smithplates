# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import sqlite3
from typing import cast
from datetime import datetime, timezone
import aiosqlite

from widget_repository_models import (
    Widget,
    WidgetNotFound,
)
from widget_repository_protocol import WidgetRepositoryServiceProtocol


class WidgetRepositoryAiosqliteService(WidgetRepositoryServiceProtocol):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_widget(
        self,
        foo: str,
        bar: int,
    ) -> str:
        cursor = await self._connection.execute(
            """INSERT INTO widgets (foo, bar) VALUES (?, ?) RETURNING id;""",
            (foo, bar),
        )
        row = await cursor.fetchone()
        if row is None:
            raise RuntimeError("INSERT RETURNING produced no row")
        return _read_str(row, 0)
    @override
    async def get_widget(
        self,
        id: str,
    ) -> Widget | WidgetNotFound:
        cursor = await self._connection.execute(
            """SELECT id, foo, bar, created_at, updated_at FROM widgets WHERE id = ?;""",
            (id,),
        )
        row = await cursor.fetchone()
        if row is None:
            return WidgetNotFound(
                message="Resource not found",
            )
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
        cursor = await self._connection.execute(
            """UPDATE widgets
SET foo = ?, bar = ?, updated_at = CURRENT_TIMESTAMP
WHERE id = ? RETURNING updated_at;""",
            (foo, bar, id),
        )
        row = await cursor.fetchone()
        return row is not None
    @override
    async def delete_widget(
        self,
        id: str,
    ) -> bool:
        cursor = await self._connection.execute(
            """DELETE FROM widgets WHERE id = ? RETURNING id;""",
            (id,),
        )
        row = await cursor.fetchone()
        return row is not None
def _read_datetime(row: sqlite3.Row, index: int) -> datetime:
    value = cast(str, row[index])
    if value.endswith("Z"):
        normalized = value[:-1] + "+00:00"
    elif " " in value and "T" not in value:
        normalized = value.replace(" ", "T", 1) + "+00:00"
    else:
        normalized = value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)

def _read_int(row: sqlite3.Row, index: int) -> int:
    return cast(int, row[index])

def _read_str(row: sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
