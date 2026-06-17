# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from datetime import datetime
from typing import cast, override

import psycopg
from generated.db.models.shipment_repository_models import (
    DeliveryState,
    PostalAddress,
    Shipment,
)
from generated.db.postgres.psycopg_transaction_run import run
from generated.db.shipment_repository_protocol import ShipmentRepositoryServiceProtocol
from psycopg.rows import dict_row


class ShipmentRepositoryPsycopgService(ShipmentRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO shipments (label, destination, state) VALUES (%s, %s, %s) RETURNING id;""",
                (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state)),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_shipment(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Shipment | None:
        async def execute() -> Shipment | None:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """SELECT shipments.id, shipments.label, shipments.destination, shipments.state, shipments.created_at
FROM shipments
WHERE id = %s;""",
                    (id,),
                )
                row = await cur.fetchone()
                if row is None:
                    return None
                return Shipment(
                    id=_read_str_col(row, "id"),
                    label=_read_str_col(row, "label"),
                    destination=_read_PostalAddress_col(row, "destination"),
                    state=_read_DeliveryState_col(row, "state"),
                    created_at=_read_datetime_col(row, "created_at"),
                )

        return await run(self._connection, transaction, execute)

    @override
    async def update_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE shipments
SET label = %s, destination = %s, state = %s
WHERE id = %s;""",
                (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state), id),
            )
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_shipment(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """DELETE FROM shipments WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
            return row is not None

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_DeliveryState(value: DeliveryState) -> str:
    present = [key for key in ("pending", "delivered") if key in value]
    if len(present) != 1:
        raise ValueError("DeliveryState union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_DeliveryState(row: tuple[object, ...], index: int) -> DeliveryState:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    present = [key for key in ("pending", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown DeliveryState discriminator: {sorted(data.keys())}")
    return cast(DeliveryState, data)


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


def _read_datetime_col(row: dict[str, object], column: str) -> datetime:
    return cast(datetime, row[column])


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_DeliveryState_col(row: dict[str, object], column: str) -> DeliveryState:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    present = [key for key in ("pending", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown DeliveryState discriminator: {sorted(data.keys())}")
    return cast(DeliveryState, data)


def _read_PostalAddress_col(row: dict[str, object], column: str) -> PostalAddress:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )
