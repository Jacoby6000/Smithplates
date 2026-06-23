# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime
from typing import cast, override

import aiosqlite
from generated.example.models.order_repository_models import (
    FulfillmentState,
    FulfillmentStateDelivered,
    FulfillmentStatePending,
    FulfillmentStateShipped,
    OrderLine,
    PostalAddress,
)
from generated.example.order_repository_protocol import (
    GetOrderResult,
    OrderRepositoryServiceProtocol,
)
from generated.example.sqlite.sqlite_transaction_run import run


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


def _map_json_timestamp(value: object) -> datetime:
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value))


def _dump_json_timestamp(value: datetime) -> str:
    return value.isoformat()


def _map_to_PostalAddress(data: dict[str, object]) -> PostalAddress:
    return PostalAddress(
        street=str(data["street"]),
        city=str(data["city"]),
    )


def _dump_PostalAddress(value: PostalAddress) -> dict[str, object]:
    return {
        "street": value.street,
        "city": value.city,
    }


def _map_to_FulfillmentState(data: dict[str, object]) -> FulfillmentState:
    present = [key for key in ("pending", "shipped", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown FulfillmentState discriminator: {sorted(data.keys())}")
    if "pending" in data:
        return FulfillmentStatePending(
            pending=str(data["pending"]),
        )
    if "shipped" in data:
        return FulfillmentStateShipped(
            shipped=_map_json_timestamp(data["shipped"]),
        )
    if "delivered" in data:
        return FulfillmentStateDelivered(
            delivered=_map_json_timestamp(data["delivered"]),
        )
    raise ValueError(f"unknown FulfillmentState discriminator: {sorted(data.keys())}")


def _dump_FulfillmentState(value: FulfillmentState) -> dict[str, object]:
    if isinstance(value, FulfillmentStatePending):
        return {"pending": value.pending}
    if isinstance(value, FulfillmentStateShipped):
        return {"shipped": _dump_json_timestamp(value.shipped)}
    if isinstance(value, FulfillmentStateDelivered):
        return {"delivered": _dump_json_timestamp(value.delivered)}
    raise TypeError(f"unsupported FulfillmentState variant: {type(value)!r}")


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _json_bind_FulfillmentState(value: FulfillmentState) -> str:
    return json.dumps(_dump_FulfillmentState(value))


def _read_FulfillmentState(row: tuple[object, ...] | sqlite3.Row, index: int) -> FulfillmentState:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_FulfillmentState(data)


def _json_bind_PostalAddress(value: PostalAddress) -> str:
    return json.dumps(_dump_PostalAddress(value))


def _read_PostalAddress(row: tuple[object, ...] | sqlite3.Row, index: int) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_PostalAddress(data)
