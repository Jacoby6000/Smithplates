# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from psycopg_transaction_run import run
from widget_repository_models import (
    Category,
)
from widget_repository_protocol import (
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
                """SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name
FROM widgets AS widgets
INNER JOIN categories AS c ON widgets.category_id = c.id
WHERE widgets.id = %s;""",
                (id,),
            )
            row = await cur.fetchone()
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


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
