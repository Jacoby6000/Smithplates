# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.models.order_repository_models import (
    OrderLine,
)
from generated.example.order_repository_protocol import (
    GetOrderResult,
    OrderRepositoryServiceProtocol,
)
from generated.example.postgres.psycopg_transaction_run import run


class OrderRepositoryPsycopgService(OrderRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_order(
        self,
        label: str | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO orders (label) VALUES (%s) RETURNING id;""",
                (label,),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_order(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetOrderResult | None:
        async def execute() -> GetOrderResult | None:
            cur = await self._connection.execute(
                """SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku
FROM orders AS orders
LEFT JOIN order_lines AS ol ON orders.id = ol.order_id
WHERE orders.id = %s;""",
                (id,),
            )
            rows = await cur.fetchall()
            if not rows:
                return None
            row = rows[0]
            order_lines: list[OrderLine] = []
            for joined_row in rows:
                if joined_row[2] is not None:
                    order_lines.append(
                        OrderLine(
                            id=_read_str(joined_row, 2),
                            order_id=None if joined_row[3] is None else _read_str(joined_row, 3),
                            sku=None if joined_row[4] is None else _read_str(joined_row, 4),
                        )
                    )
            return GetOrderResult(
                id=_read_str(row, 0),
                label=None if row[1] is None else _read_str(row, 1),
                order_lines=order_lines,
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
