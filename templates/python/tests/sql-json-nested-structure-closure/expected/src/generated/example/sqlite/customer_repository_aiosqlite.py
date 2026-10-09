# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from typing import cast, override

import aiosqlite
from generated.example.customer_repository_protocol import CustomerRepositoryServiceProtocol
from generated.example.models.customer_repository_models import (
    CollectionOnlyValue,
    ContactInfo,
    Customer,
    GeoCoordinates,
    InnerChoice,
    InnerChoiceContacts,
    InnerChoiceTimestamp,
    NestedChoice,
    NestedChoiceDeeper,
    NestedChoiceText,
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
        labels: list[str],
        contacts: list[ContactInfo],
        contact_map: dict[str, ContactInfo],
        alternate_labels: list[str] | None,
        collection_only: list[CollectionOnlyValue],
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO customers (name, contact, labels, contacts, contact_map, alternate_labels, collection_only) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id;""",
                (
                    name,
                    _json_bind_ContactInfo(contact),
                    _json_bind_collection_4c6973745b537472696e675d(labels),
                    _json_bind_collection_4c6973745b436f6e74616374496e666f5d(contacts),
                    _json_bind_collection_4d61705b537472696e672c20436f6e74616374496e666f5d(contact_map),
                    _json_bind_collection_4c6973745b537472696e675d(alternate_labels),
                    _json_bind_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d(collection_only),
                ),
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
                """SELECT customers.id, customers.name, customers.contact, customers.labels, customers.contacts, customers.contact_map, customers.alternate_labels, customers.collection_only, customers.created_at
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
                labels=_read_collection_4c6973745b537472696e675d_col(named_row, "labels"),
                contacts=_read_collection_4c6973745b436f6e74616374496e666f5d_col(named_row, "contacts"),
                contact_map=_read_collection_4d61705b537472696e672c20436f6e74616374496e666f5d_col(
                    named_row, "contact_map"
                ),
                alternate_labels=None
                if named_row["alternate_labels"] is None
                else _read_collection_4c6973745b537472696e675d_col(named_row, "alternate_labels"),
                collection_only=_read_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d_col(
                    named_row, "collection_only"
                ),
                created_at=_read_datetime_col(named_row, "created_at"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def update_customer(
        self,
        name: str,
        contact: ContactInfo,
        labels: list[str],
        contacts: list[ContactInfo],
        contact_map: dict[str, ContactInfo],
        alternate_labels: list[str] | None,
        collection_only: list[CollectionOnlyValue],
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE customers
SET name = ?, contact = ?, labels = ?, contacts = ?, contact_map = ?, alternate_labels = ?, collection_only = ?
WHERE id = ?;""",
                (
                    name,
                    _json_bind_ContactInfo(contact),
                    _json_bind_collection_4c6973745b537472696e675d(labels),
                    _json_bind_collection_4c6973745b436f6e74616374496e666f5d(contacts),
                    _json_bind_collection_4d61705b537472696e672c20436f6e74616374496e666f5d(contact_map),
                    _json_bind_collection_4c6973745b537472696e675d(alternate_labels),
                    _json_bind_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d(collection_only),
                    id,
                ),
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


def _map_to_CollectionOnlyValue(data: dict[str, object]) -> CollectionOnlyValue:
    return CollectionOnlyValue(
        label=cast(str, data["label"]),
        contact=(
            _map_to_ContactInfo(cast(dict[str, object], data["contact"])) if data["contact"] is not None else None
        ),
        choice=_map_to_NestedChoice(cast(dict[str, object], data["choice"])),
        history=(
            [_map_to_InnerChoice(cast(dict[str, object], item)) for item in cast(list[object], data["history"])]
            if data["history"] is not None
            else None
        ),
        annotations=(
            {
                str(key): _map_to_ContactInfo(cast(dict[str, object], mapped_value))
                for key, mapped_value in cast(dict[str, object], data["annotations"]).items()
            }
            if data["annotations"] is not None
            else None
        ),
    )


def _dump_CollectionOnlyValue(value: CollectionOnlyValue) -> dict[str, object]:
    return {
        "label": value.label,
        "contact": (_dump_ContactInfo(value.contact) if value.contact is not None else None),
        "choice": _dump_NestedChoice(value.choice),
        "history": ([_dump_InnerChoice(item) for item in value.history] if value.history is not None else None),
        "annotations": (
            {key: _dump_ContactInfo(mapped_value) for key, mapped_value in value.annotations.items()}
            if value.annotations is not None
            else None
        ),
    }


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


def _map_to_InnerChoice(data: dict[str, object]) -> InnerChoice:
    present = [key for key in ["contacts", "timestamp"] if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown InnerChoice discriminator: {sorted(data.keys())}")
    if "contacts" in data:
        return InnerChoiceContacts(
            contacts={
                str(key): _map_to_ContactInfo(cast(dict[str, object], mapped_value))
                for key, mapped_value in cast(dict[str, object], data["contacts"]).items()
            },
        )
    if "timestamp" in data:
        return InnerChoiceTimestamp(
            timestamp=_map_json_timestamp(data["timestamp"]),
        )
    raise ValueError(f"unknown InnerChoice discriminator: {sorted(data.keys())}")


def _dump_InnerChoice(value: InnerChoice) -> dict[str, object]:
    if isinstance(value, InnerChoiceContacts):
        return {"contacts": {key: _dump_ContactInfo(mapped_value) for key, mapped_value in value.contacts.items()}}
    if isinstance(value, InnerChoiceTimestamp):
        return {"timestamp": _dump_json_timestamp(value.timestamp)}
    raise TypeError(f"unsupported InnerChoice variant: {type(value)!r}")


def _map_to_NestedChoice(data: dict[str, object]) -> NestedChoice:
    present = [key for key in ["text", "deeper"] if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown NestedChoice discriminator: {sorted(data.keys())}")
    if "text" in data:
        return NestedChoiceText(
            text=cast(str, data["text"]),
        )
    if "deeper" in data:
        return NestedChoiceDeeper(
            deeper=_map_to_InnerChoice(cast(dict[str, object], data["deeper"])),
        )
    raise ValueError(f"unknown NestedChoice discriminator: {sorted(data.keys())}")


def _dump_NestedChoice(value: NestedChoice) -> dict[str, object]:
    if isinstance(value, NestedChoiceText):
        return {"text": value.text}
    if isinstance(value, NestedChoiceDeeper):
        return {"deeper": _dump_InnerChoice(value.deeper)}
    raise TypeError(f"unsupported NestedChoice variant: {type(value)!r}")


def _json_bind_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d(
    value: list[CollectionOnlyValue] | None,
) -> str | None:
    if value is None:
        return None
    return json.dumps([_dump_CollectionOnlyValue(item) for item in value])


def _read_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> list[CollectionOnlyValue]:
    data = json.loads(cast(str, row[index]))
    return [_map_to_CollectionOnlyValue(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b436f6c6c656374696f6e4f6e6c7956616c75655d_col(
    row: dict[str, object], column: str
) -> list[CollectionOnlyValue]:
    data = json.loads(cast(str, row[column]))
    return [_map_to_CollectionOnlyValue(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b436f6e74616374496e666f5d(value: list[ContactInfo] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([_dump_ContactInfo(item) for item in value])


def _read_collection_4c6973745b436f6e74616374496e666f5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> list[ContactInfo]:
    data = json.loads(cast(str, row[index]))
    return [_map_to_ContactInfo(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b436f6e74616374496e666f5d_col(row: dict[str, object], column: str) -> list[ContactInfo]:
    data = json.loads(cast(str, row[column]))
    return [_map_to_ContactInfo(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b537472696e675d(value: list[str] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([item for item in value])


def _read_collection_4c6973745b537472696e675d(row: tuple[object, ...] | sqlite3.Row, index: int) -> list[str]:
    data = json.loads(cast(str, row[index]))
    return [cast(str, item) for item in cast(list[object], data)]


def _read_collection_4c6973745b537472696e675d_col(row: dict[str, object], column: str) -> list[str]:
    data = json.loads(cast(str, row[column]))
    return [cast(str, item) for item in cast(list[object], data)]


def _json_bind_collection_4d61705b537472696e672c20436f6e74616374496e666f5d(
    value: dict[str, ContactInfo] | None,
) -> str | None:
    if value is None:
        return None
    return json.dumps({key: _dump_ContactInfo(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c20436f6e74616374496e666f5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, ContactInfo]:
    data = json.loads(cast(str, row[index]))
    return {
        str(key): _map_to_ContactInfo(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c20436f6e74616374496e666f5d_col(
    row: dict[str, object], column: str
) -> dict[str, ContactInfo]:
    data = json.loads(cast(str, row[column]))
    return {
        str(key): _map_to_ContactInfo(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
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


def _json_bind_CollectionOnlyValue(value: CollectionOnlyValue) -> str:
    return json.dumps(_dump_CollectionOnlyValue(value))


def _read_CollectionOnlyValue(row: tuple[object, ...] | sqlite3.Row, index: int) -> CollectionOnlyValue:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_CollectionOnlyValue(data)


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


def _read_CollectionOnlyValue_col(row: dict[str, object], column: str) -> CollectionOnlyValue:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_CollectionOnlyValue(data)


def _read_ContactInfo_col(row: dict[str, object], column: str) -> ContactInfo:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_ContactInfo(data)
