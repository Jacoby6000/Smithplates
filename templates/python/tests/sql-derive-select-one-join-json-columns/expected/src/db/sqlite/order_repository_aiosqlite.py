# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.models.order_repository_models import (
    FulfillmentState,
    OrderLine,
    PostalAddress,
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
        label: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO orders (label) VALUES (?) RETURNING id;""",
                (label,),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

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
                """SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku, ol.fulfillment AS ol_fulfillment, ol.ship_to AS ol_ship_to
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
                            fulfillment=_read_FulfillmentState(joined_row, 5),
                            ship_to=_read_PostalAddress(joined_row, 6),
                        )
                    )
            return GetOrderResult(
                id=_read_str(row, 0),
                label=_read_str(row, 1),
                order_lines=order_lines,
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _json_bind_FulfillmentState(value: FulfillmentState) -> str:
    present = [key for key in ("pending", "shipped", "delivered") if key in value]
    if len(present) != 1:
        raise ValueError("FulfillmentState union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_FulfillmentState(row: tuple[object, ...] | sqlite3.Row, index: int) -> FulfillmentState:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    present = [key for key in ("pending", "shipped", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown FulfillmentState discriminator: {sorted(data.keys())}")
    return cast(FulfillmentState, data)


def _json_bind_PostalAddress(value: PostalAddress) -> str:
    payload = {"street": cast(object, value.street), "city": cast(object, value.city)}
    return json.dumps(payload)


def _read_PostalAddress(row: tuple[object, ...] | sqlite3.Row, index: int) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )
