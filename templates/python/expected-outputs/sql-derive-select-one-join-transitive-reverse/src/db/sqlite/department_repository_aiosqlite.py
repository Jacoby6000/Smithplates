# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import sqlite3
from typing import cast
import aiosqlite
from sqlite_transaction_run import run
from department_repository_models import (
    Category,
    Department,
    Widget,
)
from department_repository_protocol import (
    GetDepartmentResult,
    DepartmentRepositoryServiceProtocol,
)


class DepartmentRepositoryAiosqliteService(DepartmentRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_department(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetDepartmentResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetDepartmentResult | None:
            cursor = await conn.execute(
                """SELECT departments.id, departments.name, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, w.id AS w_id, w.title AS w_title, w.category_id AS w_category_id
FROM departments AS departments
INNER JOIN categories AS c ON departments.id = c.department_id
INNER JOIN widgets AS w ON c.id = w.category_id
WHERE departments.id = ?;""",
                (id,),
            )
            rows = await cursor.fetchall()
            if not rows:
                return None
            row = rows[0]
            categories: list[Category] = []
            widgets: list[Widget] = []
            for joined_row in rows:
                if joined_row[2] is not None:
                    categories.append(
                        Category(
                            id=_read_str(joined_row, 2),
                            name=_read_str(joined_row, 3),
                            department_id=_read_str(joined_row, 4),
                        )
                    )
            for joined_row in rows:
                if joined_row[5] is not None:
                    widgets.append(
                        Widget(
                            id=_read_str(joined_row, 5),
                            title=_read_str(joined_row, 6),
                            category_id=_read_str(joined_row, 7),
                        )
                    )
            return GetDepartmentResult(
                id=_read_str(row, 0),
                name=_read_str(row, 1),
                categories=categories,
                widgets=widgets,
            )
        return await run(self._connection, transaction, execute)

def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
