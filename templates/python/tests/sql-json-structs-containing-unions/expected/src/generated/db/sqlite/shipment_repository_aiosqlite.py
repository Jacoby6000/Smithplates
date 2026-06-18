# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from typing import cast, override

import aiosqlite
from generated.db.models.shipment_repository_models import (
    CreateShipmentResult,
    DeleteShipmentOutput,
    DeliveryState,
    PostalAddress,
    Shipment,
    UpdateShipmentResult,
)
from generated.db.shipment_repository_protocol import ShipmentRepositoryServiceProtocol
from generated.db.sqlite.sqlite_transaction_run import run


class ShipmentRepositoryAiosqliteService(ShipmentRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateShipmentResult:
        async def execute(conn: aiosqlite.Connection) -> CreateShipmentResult:
            cursor = await conn.execute(
                """INSERT INTO shipments (label, destination, state) VALUES (?, ?, ?) RETURNING id, created_at;""",
                (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state)),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateShipmentResult(
                id=_read_str_col(named_row, "id"),
                created_at=_read_datetime_col(named_row, "created_at"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_shipment(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Shipment | None:
        async def execute(conn: aiosqlite.Connection) -> Shipment | None:
            cursor = await conn.execute(
                """SELECT shipments.id, shipments.label, shipments.destination, shipments.state, shipments.created_at
FROM shipments
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            named_row = _as_sqlite_named_row(cursor, row)
            return Shipment(
                id=_read_str_col(named_row, "id"),
                label=_read_str_col(named_row, "label"),
                destination=_read_PostalAddress_col(named_row, "destination"),
                state=_read_DeliveryState_col(named_row, "state"),
                created_at=_read_datetime_col(named_row, "created_at"),
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
        transaction: aiosqlite.Connection | None = None,
    ) -> UpdateShipmentResult:
        async def execute(conn: aiosqlite.Connection) -> UpdateShipmentResult:
            cursor = await conn.execute(
                """UPDATE shipments
SET label = ?, destination = ?, state = ?
WHERE id = ? RETURNING id, created_at;""",
                (label, _json_bind_PostalAddress(destination), _json_bind_DeliveryState(state), id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return UpdateShipmentResult(
                id=_read_str(row, 0),
                created_at=_read_datetime(row, 1),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def delete_shipment(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> DeleteShipmentOutput:
        async def execute(conn: aiosqlite.Connection) -> DeleteShipmentOutput:
            cursor = await conn.execute(
                """DELETE FROM shipments WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return DeleteShipmentOutput(deleted=row is not None)

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


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _json_bind_DeliveryState(value: DeliveryState) -> str:
    present = [key for key in ("pending", "delivered") if key in value]
    if len(present) != 1:
        raise ValueError("DeliveryState union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_DeliveryState(row: tuple[object, ...] | sqlite3.Row, index: int) -> DeliveryState:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    present = [key for key in ("pending", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown DeliveryState discriminator: {sorted(data.keys())}")
    return cast(DeliveryState, data)


def _json_bind_PostalAddress(value: PostalAddress) -> str:
    payload = {"street": cast(object, value.street), "city": cast(object, value.city)}
    return json.dumps(payload)


def _read_PostalAddress(row: tuple[object, ...] | sqlite3.Row, index: int) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )


def _read_datetime_col(row: dict[str, object], column: str) -> datetime:
    value = cast(str, row[column])
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


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])


def _read_DeliveryState_col(row: dict[str, object], column: str) -> DeliveryState:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    present = [key for key in ("pending", "delivered") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown DeliveryState discriminator: {sorted(data.keys())}")
    return cast(DeliveryState, data)


def _read_PostalAddress_col(row: dict[str, object], column: str) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
    )
