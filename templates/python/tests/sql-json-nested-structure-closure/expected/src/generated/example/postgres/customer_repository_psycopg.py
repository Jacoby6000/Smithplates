# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from datetime import datetime
from typing import cast, override

import psycopg
from generated.example.customer_repository_protocol import CustomerRepositoryServiceProtocol
from generated.example.models.customer_repository_models import (
    ContactInfo,
    Customer,
    GeoCoordinates,
    PostalAddress,
)
from generated.example.postgres.psycopg_transaction_run import run
from psycopg.rows import dict_row


class CustomerRepositoryPsycopgService(CustomerRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_customer(
        self,
        name: str,
        contact: ContactInfo,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO customers (name, contact) VALUES (%s, %s) RETURNING id;""",
                (name, _json_bind_ContactInfo(contact)),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_customer(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Customer | None:
        async def execute() -> Customer | None:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """SELECT customers.id, customers.name, customers.contact, customers.created_at
FROM customers
WHERE id = %s;""",
                    (id,),
                )
                row = await cur.fetchone()
                if row is None:
                    return None
                return Customer(
                    id=_read_str_col(row, "id"),
                    name=_read_str_col(row, "name"),
                    contact=_read_ContactInfo_col(row, "contact"),
                    created_at=_read_datetime_col(row, "created_at"),
                )

        return await run(self._connection, transaction, execute)

    @override
    async def update_customer(
        self,
        name: str,
        contact: ContactInfo,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE customers
SET name = %s, contact = %s
WHERE id = %s;""",
                (name, _json_bind_ContactInfo(contact), id),
            )
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_customer(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """DELETE FROM customers WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
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


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_ContactInfo(value: ContactInfo) -> str:
    return json.dumps(_dump_ContactInfo(value))


def _read_ContactInfo(row: tuple[object, ...], index: int) -> ContactInfo:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_ContactInfo(data)


def _read_datetime_col(row: dict[str, object], column: str) -> datetime:
    return cast(datetime, row[column])


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_ContactInfo_col(row: dict[str, object], column: str) -> ContactInfo:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_ContactInfo(data)
