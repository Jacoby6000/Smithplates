# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import sqlite3
from typing import cast
import json
from datetime import datetime, timezone
import aiosqlite

from shipment_repository_models import (
    PostalAddress,
    Shipment,
    DeliveryState,
)
from shipment_repository_protocol import ShipmentRepositoryServiceProtocol


class ShipmentRepositoryAiosqliteService(ShipmentRepositoryServiceProtocol):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
    ) -> str:
        cursor = await self._connection.execute(
            """INSERT INTO shipments (label, destination, state) VALUES (?, ?, ?) RETURNING id;""",
            (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state)),
        )
        row = await cursor.fetchone()
        if row is None:
            raise RuntimeError("INSERT RETURNING produced no row")
        return _read_str(row, 0)
    @override
    async def get_shipment(
        self,
        id: str,
    ) -> Shipment | None:
        cursor = await self._connection.execute(
            """SELECT id, label, destination, state, created_at FROM shipments WHERE id = ?;""",
            (id,),
        )
        row = await cursor.fetchone()
        if row is None:
            return None
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
        cursor = await self._connection.execute(
            """UPDATE shipments
SET label = ?, destination = ?, state = ?
WHERE id = ?;""",
            (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state), id),
        )
        return cursor.rowcount > 0
    @override
    async def delete_shipment(
        self,
        id: str,
    ) -> bool:
        cursor = await self._connection.execute(
            """DELETE FROM shipments WHERE id = ? RETURNING id;""",
            (id,),
        )
        row = await cursor.fetchone()
        return row is not None
def _read_datetime(row: sqlite3.Row, index: int) -> datetime:
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

def _read_str(row: sqlite3.Row, index: int) -> str:
    return cast(str, row[index])

def _json_bind_DeliveryState(value: DeliveryState) -> str:
    present = [key for key in ("pending", "delivered") if key in value]
    if len(present) != 1:
        raise ValueError("DeliveryState union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_DeliveryState(row: sqlite3.Row, index: int) -> DeliveryState:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    present = [key for key in ("pending", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown DeliveryState discriminator: {sorted(data.keys())}")
    return cast(DeliveryState, data)

def _json_bind_PostalAddress(value: PostalAddress) -> str:
    payload = { "street": cast(object, getattr(value, "street")), "city": cast(object, getattr(value, "city")) }
    return json.dumps(payload)


def _read_PostalAddress(row: sqlite3.Row, index: int) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )
