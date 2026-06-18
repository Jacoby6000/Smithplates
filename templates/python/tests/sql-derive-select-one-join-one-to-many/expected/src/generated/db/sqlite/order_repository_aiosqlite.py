# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.models.order_repository_models import (
    CreateOrderResult,
    OrderLine,
)
from generated.db.order_repository_protocol import (
    GetOrderResult,
    OrderRepositoryServiceProtocol,
)
from generated.db.sqlite.sqlite_transaction_run import run


class OrderRepositoryAiosqliteService(OrderRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_order(
        self,
        label: str | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateOrderResult:
        async def execute(conn: aiosqlite.Connection) -> CreateOrderResult:
            cursor = await conn.execute(
                """INSERT INTO orders (label) VALUES (?) RETURNING id;""",
                (label,),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateOrderResult(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_order(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetOrderResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetOrderResult | None:
            cursor = await conn.execute(
                """SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku
FROM orders AS orders
LEFT JOIN order_lines AS ol ON orders.id = ol.order_id
WHERE orders.id = ?;""",
                (id,),
            )
            rows = list(await cursor.fetchall())
            if not rows:
                return None
            row = rows[0]
            order_lines: list[OrderLine] = []
            for joined_row in rows:
                if joined_row[2] is not None:
                    order_lines.append(
                        OrderLine(
                            id=_read_str(joined_row, 2),
                            order_id=_read_str(joined_row, 3),
                            sku=_read_str(joined_row, 4),
                        )
                    )
            return GetOrderResult(
                id=_read_str(row, 0),
                label=_read_str(row, 1),
                order_lines=order_lines,
            )

        return await run(self._connection, transaction, execute)


def _as_sqlite_named_row(
    cursor: sqlite3.Cursor | aiosqlite.Cursor,
    row: tuple[object, ...] | sqlite3.Row,
) -> dict[str, object]:
    if type(row) is not tuple:
        named = cast(sqlite3.Row, row)
        return {key: cast(object, named[key]) for key in named}
    description = cast(tuple[tuple[object, ...], ...], cursor.description)
    return {cast(str, column[0]): row[index] for index, column in enumerate(description)}


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
