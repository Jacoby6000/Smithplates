# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

from typing import cast
import uuid
import psycopg
from psycopg_transaction_run import run
from widget_repository_models import (
    Category,
    Widget,
    GetWidgetResult,
)
from widget_repository_protocol import WidgetRepositoryServiceProtocol


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
LEFT JOIN categories AS c ON widgets.category_id = c.id
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
                category=None if row[3] is None else Category(
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
