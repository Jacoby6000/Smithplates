# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.models.widget_repository_models import (
    Category,
    Department,
)
from generated.db.sqlite.sqlite_transaction_run import run
from generated.db.widget_repository_protocol import (
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
                """SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, d.id AS d_id, d.name AS d_name
FROM widgets AS widgets
INNER JOIN categories AS c ON widgets.category_id = c.id
INNER JOIN departments AS d ON c.department_id = d.id
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
                    department_id=_read_str(row, 5),
                ),
                department=Department(
                    id=_read_str(row, 6),
                    name=_read_str(row, 7),
                ),
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
