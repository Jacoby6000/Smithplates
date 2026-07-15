# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from typing import cast, override

import aiosqlite
from generated.example.customer_repository_protocol import CustomerRepositoryServiceProtocol
from generated.example.models.customer_repository_models import (
    ContactInfo,
    Customer,
    GeoCoordinates,
    PostalAddress,
)
from generated.example.sqlite.sqlite_transaction_run import run


class CustomerRepositoryAiosqliteService(CustomerRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_customer(
        self,
        name: str,
        contact: ContactInfo,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO customers (name, contact) VALUES (?, ?) RETURNING id;""",
                (name, _json_bind_ContactInfo(contact)),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_customer(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Customer | None:
        async def execute(conn: aiosqlite.Connection) -> Customer | None:
            cursor = await conn.execute(
                """SELECT customers.id, customers.name, customers.contact, customers.created_at
FROM customers
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            named_row = _as_sqlite_named_row(cursor, row)
            return Customer(
                id=_read_str_col(named_row, "id"),
                name=_read_str_col(named_row, "name"),
                contact=_read_ContactInfo_col(named_row, "contact"),
                created_at=_read_datetime_col(named_row, "created_at"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def update_customer(
        self,
        name: str,
        contact: ContactInfo,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE customers
SET name = ?, contact = ?
WHERE id = ?;""",
                (name, _json_bind_ContactInfo(contact), id),
            )
            return cursor.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_customer(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """DELETE FROM customers WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return row is not None

        return await run(self._connection, transaction, execute)


def _map_json_timestamp(value: object) -> datetime:
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value))


def _dump_json_timestamp(value: datetime) -> str:
    return value.isoformat()


def _map_to_ContactInfo(data: dict[str, object]) -> ContactInfo:
    return ContactInfo(
        email=cast(str, data["email"]),
        address=_map_to_PostalAddress(cast(dict[str, object], data["address"])),
    )


def _dump_ContactInfo(value: ContactInfo) -> dict[str, object]:
    return {
        "email": value.email,
        "address": _dump_PostalAddress(value.address),
    }


def _map_to_GeoCoordinates(data: dict[str, object]) -> GeoCoordinates:
    return GeoCoordinates(
        lat=cast(float, data["lat"]),
        lng=cast(float, data["lng"]),
        recorded_at=_map_json_timestamp(data["recorded_at"]),
    )


def _dump_GeoCoordinates(value: GeoCoordinates) -> dict[str, object]:
    return {
        "lat": value.lat,
        "lng": value.lng,
        "recorded_at": _dump_json_timestamp(value.recorded_at),
    }


def _map_to_PostalAddress(data: dict[str, object]) -> PostalAddress:
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
        coords=_map_to_GeoCoordinates(cast(dict[str, object], data["coords"])),
    )


def _dump_PostalAddress(value: PostalAddress) -> dict[str, object]:
    return {
        "street": value.street,
        "city": value.city,
        "coords": _dump_GeoCoordinates(value.coords),
    }


def _as_sqlite_named_row(
    cursor: sqlite3.Cursor | aiosqlite.Cursor,
    row: tuple[object, ...] | sqlite3.Row,
) -> dict[str, object]:
    if type(row) is not tuple:
        named = cast(sqlite3.Row, row)
        return {key: cast(object, named[key]) for key in named}
    description = cast(tuple[tuple[object, ...], ...], cursor.description)
    return {cast(str, column[0]): row[index] for index, column in enumerate(description)}


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _json_bind_ContactInfo(value: ContactInfo) -> str:
    return json.dumps(_dump_ContactInfo(value))


def _read_ContactInfo(row: tuple[object, ...] | sqlite3.Row, index: int) -> ContactInfo:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_ContactInfo(data)


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


def _read_ContactInfo_col(row: dict[str, object], column: str) -> ContactInfo:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_ContactInfo(data)
