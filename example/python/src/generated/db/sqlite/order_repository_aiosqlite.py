# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from typing import cast, override

import aiosqlite
from generated.db.model.order_repository_models import (
    FulfillmentState,
    OrderLine,
)
from generated.db.order_repository_protocol import (
    GetOrderRecordResult,
    OrderRepositoryServiceProtocol,
)
from generated.db.sqlite.sqlite_transaction_run import run


class OrderRepositoryAiosqliteService(OrderRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_order_record(
        self,
        label: str,
        status: OrderStatus,
        priority: OrderPriority,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
"""INSERT INTO orders (label, status, priority) VALUES (?, ?, ?) RETURNING id;"""
,
                (label, status, priority),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)
        return await run(self._connection, transaction, execute)
    @override
    async def get_order_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetOrderRecordResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetOrderRecordResult | None:
            cursor = await conn.execute(
"""SELECT orders.id, orders.label, orders.status, orders.priority, orders.created_at, orders.updated_at, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.pet_id AS ol_pet_id, ol.quantity AS ol_quantity, ol.unit_price_cents AS ol_unit_price_cents, ol.fulfillment AS ol_fulfillment
FROM orders AS orders
LEFT JOIN order_lines AS ol ON orders.id = ol.order_id
WHERE orders.id = ?;"""
,
                (id,),
            )
            rows = list(await cursor.fetchall())
            if not rows:
                return None
            row = rows[0]
            order_lines: list[OrderLine] = []
            for joined_row in rows:
                if joined_row[6] is not None:
                    order_lines.append(
                        OrderLine(
                            id=_read_str(joined_row, 6),
                            order_id=_read_str(joined_row, 7),
                            pet_id=_read_str(joined_row, 8),
                            quantity=_read_int(joined_row, 9),
                            unit_price_cents=_read_int(joined_row, 10),
                            fulfillment=_read_FulfillmentState(joined_row, 11),
                        )
                    )
            return GetOrderRecordResult(
                id=_read_str(row, 0),
                label=_read_str(row, 1),
                status=_read_str(row, 2),
                priority=_read_int(row, 3),
                created_at=_read_datetime(row, 4),
                updated_at=_read_datetime(row, 5),
                order_lines=order_lines,
            )
        return await run(self._connection, transaction, execute)

def _read_datetime(row: tuple[object, ...] | sqlite3.Row, index: int) -> datetime:
    value = cast(str, row[index])
    if value.endswith("Z"):
        normalized = value[:-1] + "+00:00"
    elif " " in value and "T" not in value:
        normalized = value.replace(" ", "T", 1) + "+00:00"
    else:
        normalized = value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)

def _read_int(row: tuple[object, ...] | sqlite3.Row, index: int) -> int:
    return cast(int, row[index])

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
