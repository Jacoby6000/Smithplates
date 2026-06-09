# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import sqlite3
from typing import cast
import aiosqlite
from sqlite_transaction_run import run
from widget_repository_models import (
    Category,
    Widget,
)
from widget_repository_protocol import (
    GetWidgetResult,
    WidgetRepositoryServiceProtocol,
)


class WidgetRepositoryAiosqliteService(WidgetRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_widget(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetWidgetResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetWidgetResult | None:
            cursor = await conn.execute(
                """SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name
FROM widgets AS widgets
INNER JOIN categories AS c ON widgets.category_id = c.id
WHERE widgets.id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return GetWidgetResult(
                id=_read_str(row, 0),
                title=_read_str(row, 1),
                category_id=_read_str(row, 2),
                category=Category(
                    id=_read_str(row, 3),
                    name=_read_str(row, 4),
                ),
            )
        return await run(self._connection, transaction, execute)

def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
