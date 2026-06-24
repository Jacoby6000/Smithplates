# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from datetime import datetime
from typing import cast, override

import psycopg
from psycopg.rows import dict_row

from generated.petstore.db.models.order_repository_models import (
    CreateOrderRecordOutput,
    FulfillmentState,
    FulfillmentStateDelivered,
    FulfillmentStatePending,
    FulfillmentStateShipped,
    OrderLine,
)
from generated.petstore.db.order_priority import OrderPriority
from generated.petstore.db.order_repository_protocol import (
    GetOrderRecordResult,
    OrderRepositoryServiceProtocol,
)
from generated.petstore.db.order_status import OrderStatus
from generated.petstore.db.postgres.psycopg_transaction_run import run


class OrderRepositoryPsycopgService(OrderRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_order_record(
        self,
        label: str,
        status: OrderStatus,
        priority: OrderPriority,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> CreateOrderRecordOutput:
        async def execute() -> CreateOrderRecordOutput:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """INSERT INTO orders (label, status, priority) VALUES (%s, %s, %s) RETURNING id;""",
                    (label, status, priority),
                )
                row = await cur.fetchone()
                if row is None:
                    raise RuntimeError("INSERT RETURNING produced no row")
                return CreateOrderRecordOutput(
                    id=_read_str_col(row, "id"),
                )

        return await run(self._connection, transaction, execute)

    @override
    async def get_order_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetOrderRecordResult | None:
        async def execute() -> GetOrderRecordResult | None:
            cur = await self._connection.execute(
                """SELECT orders.id, orders.label, orders.status, orders.priority, orders.created_at, orders.updated_at, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.pet_id AS ol_pet_id, ol.quantity AS ol_quantity, ol.unit_price_cents AS ol_unit_price_cents, ol.fulfillment AS ol_fulfillment
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
                status=OrderStatus(_read_str(row, 2)),
                priority=OrderPriority(_read_int(row, 3)),
                created_at=_read_datetime(row, 4),
                updated_at=_read_datetime(row, 5),
                order_lines=order_lines,
            )

        return await run(self._connection, transaction, execute)


def _map_json_timestamp(value: object) -> datetime:
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value))


def _dump_json_timestamp(value: datetime) -> str:
    return value.isoformat()


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


def _read_datetime(row: tuple[object, ...], index: int) -> datetime:
    return cast(datetime, row[index])


def _read_int(row: tuple[object, ...], index: int) -> int:
    return cast(int, row[index])


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_FulfillmentState(value: FulfillmentState) -> str:
    return json.dumps(_dump_FulfillmentState(value))


def _read_FulfillmentState(row: tuple[object, ...], index: int) -> FulfillmentState:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_FulfillmentState(data)


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
