# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.models.widget_repository_models import (
    Category,
    Department,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.widget_repository_protocol import (
    GetWidgetResult,
    WidgetRepositoryServiceProtocol,
)


class WidgetRepositoryPsycopgService(WidgetRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_widget(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetWidgetResult | None:
        async def execute() -> GetWidgetResult | None:
            cur = await self._connection.execute(
                """SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, d.id AS d_id, d.name AS d_name
FROM widgets AS widgets
INNER JOIN categories AS c ON widgets.category_id = c.id
INNER JOIN departments AS d ON c.department_id = d.id
WHERE widgets.id = %s;""",
                (id,),
            )
            row = await cur.fetchone()
            if row is None:
                return None
            return GetWidgetResult(
                id=_read_str(row, 0),
                title=None if row[1] is None else _read_str(row, 1),
                category_id=_read_str(row, 2),
                category=Category(
                    id=_read_str(row, 3),
                    name=None if row[4] is None else _read_str(row, 4),
                    department_id=_read_str(row, 5),
                ),
                department=Department(
                    id=_read_str(row, 6),
                    name=None if row[7] is None else _read_str(row, 7),
                ),
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
