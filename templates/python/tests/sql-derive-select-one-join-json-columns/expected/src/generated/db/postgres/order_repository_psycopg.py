# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from typing import cast, override

import psycopg
from generated.db.models.order_repository_models import (
    CreateOrderResult,
    FulfillmentState,
    OrderLine,
    PostalAddress,
)
from generated.db.order_repository_protocol import (
    GetOrderResult,
    OrderRepositoryServiceProtocol,
)
from generated.db.postgres.psycopg_transaction_run import run
from psycopg.rows import dict_row


class OrderRepositoryPsycopgService(OrderRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_order(
        self,
        label: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> CreateOrderResult:
        async def execute() -> CreateOrderResult:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """INSERT INTO orders (label) VALUES (%s) RETURNING id;""",
                    (label,),
                )
                row = await cur.fetchone()
                if row is None:
                    raise RuntimeError("INSERT RETURNING produced no row")
                return CreateOrderResult(
                    id=_read_str_col(row, "id"),
                )

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
                """SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku, ol.fulfillment AS ol_fulfillment, ol.ship_to AS ol_ship_to
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


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_FulfillmentState(value: FulfillmentState) -> str:
    present = [key for key in ("pending", "shipped", "delivered") if key in value]
    if len(present) != 1:
        raise ValueError("FulfillmentState union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_FulfillmentState(row: tuple[object, ...], index: int) -> FulfillmentState:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    present = [key for key in ("pending", "shipped", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown FulfillmentState discriminator: {sorted(data.keys())}")
    return cast(FulfillmentState, data)


def _json_bind_PostalAddress(value: PostalAddress) -> str:
    payload = {"street": cast(object, value.street), "city": cast(object, value.city)}
    return json.dumps(payload)


def _read_PostalAddress(row: tuple[object, ...], index: int) -> PostalAddress:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
