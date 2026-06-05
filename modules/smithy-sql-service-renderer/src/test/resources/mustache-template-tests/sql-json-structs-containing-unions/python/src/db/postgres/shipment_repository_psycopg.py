# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

from typing import cast
import uuid
import json
from datetime import datetime
import psycopg

from shipment_repository_models import (
    PostalAddress,
    Shipment,
    ShipmentNotFound,
    DeliveryState,
)
from shipment_repository_protocol import ShipmentRepositoryServiceProtocol


class ShipmentRepositoryPsycopgService(ShipmentRepositoryServiceProtocol):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
    ) -> str:
        cur = await self._connection.execute(
            """INSERT INTO shipments (label, destination, state) VALUES (%s, %s, %s) RETURNING id;""",
            (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state)),
        )
        row = await cur.fetchone()
        if row is None:
            raise RuntimeError("INSERT RETURNING produced no row")
        return _read_str(row, 0)
    @override
    async def get_shipment(
        self,
        id: str,
    ) -> Shipment | ShipmentNotFound:
        cur = await self._connection.execute(
            """SELECT id, label, destination, state, created_at FROM shipments WHERE id = %s;""",
            (id,),
        )
        row = await cur.fetchone()
        if row is None:
            return ShipmentNotFound(
                message="Resource not found",
            )
        return Shipment(
            id=_read_str(row, 0),
            label=_read_str(row, 1),
            destination=_read_PostalAddress(row, 2),
            state=_read_DeliveryState(row, 3),
            created_at=_read_datetime(row, 4),
        )
    @override
    async def update_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        id: str,
    ) -> bool:
        cur = await self._connection.execute(
            """UPDATE shipments
SET label = %s, destination = %s, state = %s
WHERE id = %s;""",
            (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state), id),
        )
        return cur.rowcount > 0
    @override
    async def delete_shipment(
        self,
        id: str,
    ) -> bool:
        cur = await self._connection.execute(
            """DELETE FROM shipments WHERE id = %s RETURNING id;""",
            (id,),
        )
        row = await cur.fetchone()
        return row is not None
def _read_datetime(row: tuple[object, ...], index: int) -> datetime:
    return cast(datetime, row[index])

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
    payload = { "street": cast(object, getattr(value, "street")), "city": cast(object, getattr(value, "city")) }
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
