# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from department_repository_models import (
    Category,
    Widget,
)
from department_repository_protocol import (
    DepartmentRepositoryServiceProtocol,
    GetDepartmentResult,
)
from psycopg_transaction_run import run


class DepartmentRepositoryPsycopgService(DepartmentRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_department(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetDepartmentResult | None:
        async def execute() -> GetDepartmentResult | None:
            cur = await self._connection.execute(
                """SELECT departments.id, departments.name, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, w.id AS w_id, w.title AS w_title, w.category_id AS w_category_id
FROM departments AS departments
LEFT JOIN categories AS c ON departments.id = c.department_id
LEFT JOIN widgets AS w ON c.id = w.category_id
WHERE departments.id = %s;""",
                (id,),
            )
            rows = await cur.fetchall()
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


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
