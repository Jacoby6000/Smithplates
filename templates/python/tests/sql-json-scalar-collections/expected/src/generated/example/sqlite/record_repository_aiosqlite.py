# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime
from decimal import Decimal
from typing import cast, override

import aiosqlite
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from generated.example.sqlite.sqlite_transaction_run import run


class RecordRepositoryAiosqliteService(RecordRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_record(
        self,
        instants: list[datetime],
        instant_map: dict[str, datetime],
        amounts: list[Decimal],
        amount_map: dict[str, Decimal],
        payloads: list[bytes],
        payload_map: dict[str, bytes],
        instant_groups: list[list[datetime]],
        nested_amounts: dict[str, dict[str, Decimal]],
        optional_instants: list[datetime] | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO records (instants, instant_map, amounts, amount_map, payloads, payload_map, instant_groups, nested_amounts, optional_instants) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id;""",
                (
                    _json_bind_collection_4c6973745b54696d657374616d705d(instants),
                    _json_bind_collection_4d61705b537472696e672c2054696d657374616d705d(instant_map),
                    _json_bind_collection_4c6973745b426967446563696d616c5d(amounts),
                    _json_bind_collection_4d61705b537472696e672c20426967446563696d616c5d(amount_map),
                    _json_bind_collection_4c6973745b426c6f625d(payloads),
                    _json_bind_collection_4d61705b537472696e672c20426c6f625d(payload_map),
                    _json_bind_collection_4c6973745b4c6973745b54696d657374616d705d5d(instant_groups),
                    _json_bind_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
                        nested_amounts
                    ),
                    _json_bind_collection_4c6973745b54696d657374616d705d(optional_instants),
                ),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Record | None:
        async def execute(conn: aiosqlite.Connection) -> Record | None:
            cursor = await conn.execute(
                """SELECT records.id, records.instants, records.instant_map, records.amounts, records.amount_map, records.payloads, records.payload_map, records.instant_groups, records.nested_amounts, records.optional_instants
FROM records
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            named_row = _as_sqlite_named_row(cursor, row)
            return Record(
                id=_read_str_col(named_row, "id"),
                instants=_read_collection_4c6973745b54696d657374616d705d_col(named_row, "instants"),
                instant_map=_read_collection_4d61705b537472696e672c2054696d657374616d705d_col(named_row, "instant_map"),
                amounts=_read_collection_4c6973745b426967446563696d616c5d_col(named_row, "amounts"),
                amount_map=_read_collection_4d61705b537472696e672c20426967446563696d616c5d_col(named_row, "amount_map"),
                payloads=_read_collection_4c6973745b426c6f625d_col(named_row, "payloads"),
                payload_map=_read_collection_4d61705b537472696e672c20426c6f625d_col(named_row, "payload_map"),
                instant_groups=_read_collection_4c6973745b4c6973745b54696d657374616d705d5d_col(
                    named_row, "instant_groups"
                ),
                nested_amounts=_read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d_col(
                    named_row, "nested_amounts"
                ),
                optional_instants=None
                if named_row["optional_instants"] is None
                else _read_collection_4c6973745b54696d657374616d705d_col(named_row, "optional_instants"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def update_record(
        self,
        instants: list[datetime],
        instant_map: dict[str, datetime],
        amounts: list[Decimal],
        amount_map: dict[str, Decimal],
        payloads: list[bytes],
        payload_map: dict[str, bytes],
        instant_groups: list[list[datetime]],
        nested_amounts: dict[str, dict[str, Decimal]],
        optional_instants: list[datetime] | None,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE records
SET instants = ?, instant_map = ?, amounts = ?, amount_map = ?, payloads = ?, payload_map = ?, instant_groups = ?, nested_amounts = ?, optional_instants = ?
WHERE id = ?;""",
                (
                    _json_bind_collection_4c6973745b54696d657374616d705d(instants),
                    _json_bind_collection_4d61705b537472696e672c2054696d657374616d705d(instant_map),
                    _json_bind_collection_4c6973745b426967446563696d616c5d(amounts),
                    _json_bind_collection_4d61705b537472696e672c20426967446563696d616c5d(amount_map),
                    _json_bind_collection_4c6973745b426c6f625d(payloads),
                    _json_bind_collection_4d61705b537472696e672c20426c6f625d(payload_map),
                    _json_bind_collection_4c6973745b4c6973745b54696d657374616d705d5d(instant_groups),
                    _json_bind_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
                        nested_amounts
                    ),
                    _json_bind_collection_4c6973745b54696d657374616d705d(optional_instants),
                    id,
                ),
            )
            return cursor.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """DELETE FROM records WHERE id = ? RETURNING id;""",
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


def _json_bind_collection_4c6973745b426967446563696d616c5d(value: list[Decimal] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([str(item) for item in value])


def _read_collection_4c6973745b426967446563696d616c5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> list[Decimal]:
    data = json.loads(cast(str, row[index]))
    return [Decimal(str(item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b426967446563696d616c5d_col(row: dict[str, object], column: str) -> list[Decimal]:
    data = json.loads(cast(str, row[column]))
    return [Decimal(str(item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b426c6f625d(value: list[bytes] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([item.hex() for item in value])


def _read_collection_4c6973745b426c6f625d(row: tuple[object, ...] | sqlite3.Row, index: int) -> list[bytes]:
    data = json.loads(cast(str, row[index]))
    return [bytes.fromhex(cast(str, item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b426c6f625d_col(row: dict[str, object], column: str) -> list[bytes]:
    data = json.loads(cast(str, row[column]))
    return [bytes.fromhex(cast(str, item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b4c6973745b54696d657374616d705d5d(value: list[list[datetime]] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([[_dump_json_timestamp(item) for item in item] for item in value])


def _read_collection_4c6973745b4c6973745b54696d657374616d705d5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> list[list[datetime]]:
    data = json.loads(cast(str, row[index]))
    return [[_map_json_timestamp(item) for item in cast(list[object], item)] for item in cast(list[object], data)]


def _read_collection_4c6973745b4c6973745b54696d657374616d705d5d_col(
    row: dict[str, object], column: str
) -> list[list[datetime]]:
    data = json.loads(cast(str, row[column]))
    return [[_map_json_timestamp(item) for item in cast(list[object], item)] for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b54696d657374616d705d(value: list[datetime] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([_dump_json_timestamp(item) for item in value])


def _read_collection_4c6973745b54696d657374616d705d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> list[datetime]:
    data = json.loads(cast(str, row[index]))
    return [_map_json_timestamp(item) for item in cast(list[object], data)]


def _read_collection_4c6973745b54696d657374616d705d_col(row: dict[str, object], column: str) -> list[datetime]:
    data = json.loads(cast(str, row[column]))
    return [_map_json_timestamp(item) for item in cast(list[object], data)]


def _json_bind_collection_4d61705b537472696e672c20426967446563696d616c5d(
    value: dict[str, Decimal] | None,
) -> str | None:
    if value is None:
        return None
    return json.dumps({key: str(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c20426967446563696d616c5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, Decimal]:
    data = json.loads(cast(str, row[index]))
    return {str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()}


def _read_collection_4d61705b537472696e672c20426967446563696d616c5d_col(
    row: dict[str, object], column: str
) -> dict[str, Decimal]:
    data = json.loads(cast(str, row[column]))
    return {str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()}


def _json_bind_collection_4d61705b537472696e672c20426c6f625d(value: dict[str, bytes] | None) -> str | None:
    if value is None:
        return None
    return json.dumps({key: mapped_value.hex() for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c20426c6f625d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, bytes]:
    data = json.loads(cast(str, row[index]))
    return {
        str(key): bytes.fromhex(cast(str, mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c20426c6f625d_col(row: dict[str, object], column: str) -> dict[str, bytes]:
    data = json.loads(cast(str, row[column]))
    return {
        str(key): bytes.fromhex(cast(str, mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
    value: dict[str, dict[str, Decimal]] | None,
) -> str | None:
    if value is None:
        return None
    return json.dumps(
        {
            key: {key: str(mapped_value) for key, mapped_value in mapped_value.items()}
            for key, mapped_value in value.items()
        }
    )


def _read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, dict[str, Decimal]]:
    data = json.loads(cast(str, row[index]))
    return {
        str(key): {
            str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], mapped_value).items()
        }
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d_col(
    row: dict[str, object], column: str
) -> dict[str, dict[str, Decimal]]:
    data = json.loads(cast(str, row[column]))
    return {
        str(key): {
            str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], mapped_value).items()
        }
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c2054696d657374616d705d(value: dict[str, datetime] | None) -> str | None:
    if value is None:
        return None
    return json.dumps({key: _dump_json_timestamp(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c2054696d657374616d705d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, datetime]:
    data = json.loads(cast(str, row[index]))
    return {str(key): _map_json_timestamp(mapped_value) for key, mapped_value in cast(dict[str, object], data).items()}


def _read_collection_4d61705b537472696e672c2054696d657374616d705d_col(
    row: dict[str, object], column: str
) -> dict[str, datetime]:
    data = json.loads(cast(str, row[column]))
    return {str(key): _map_json_timestamp(mapped_value) for key, mapped_value in cast(dict[str, object], data).items()}


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


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
