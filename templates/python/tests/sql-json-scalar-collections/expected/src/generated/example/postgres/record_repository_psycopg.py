# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from typing import cast, override

import psycopg
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb


class RecordRepositoryPsycopgService(RecordRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
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
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO records (instants, instant_map, amounts, amount_map, payloads, payload_map, instant_groups, nested_amounts, optional_instants) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id;""",
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
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Record | None:
        async def execute() -> Record | None:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """SELECT records.id, records.instants, records.instant_map, records.amounts, records.amount_map, records.payloads, records.payload_map, records.instant_groups, records.nested_amounts, records.optional_instants
FROM records
WHERE id = %s;""",
                    (id,),
                )
                row = await cur.fetchone()
                if row is None:
                    return None
                return Record(
                    id=_read_str_col(row, "id"),
                    instants=_read_collection_4c6973745b54696d657374616d705d_col(row, "instants"),
                    instant_map=_read_collection_4d61705b537472696e672c2054696d657374616d705d_col(row, "instant_map"),
                    amounts=_read_collection_4c6973745b426967446563696d616c5d_col(row, "amounts"),
                    amount_map=_read_collection_4d61705b537472696e672c20426967446563696d616c5d_col(row, "amount_map"),
                    payloads=_read_collection_4c6973745b426c6f625d_col(row, "payloads"),
                    payload_map=_read_collection_4d61705b537472696e672c20426c6f625d_col(row, "payload_map"),
                    instant_groups=_read_collection_4c6973745b4c6973745b54696d657374616d705d5d_col(
                        row, "instant_groups"
                    ),
                    nested_amounts=_read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d_col(
                        row, "nested_amounts"
                    ),
                    optional_instants=None
                    if row["optional_instants"] is None
                    else _read_collection_4c6973745b54696d657374616d705d_col(row, "optional_instants"),
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
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE records
SET instants = %s, instant_map = %s, amounts = %s, amount_map = %s, payloads = %s, payload_map = %s, instant_groups = %s, nested_amounts = %s, optional_instants = %s
WHERE id = %s;""",
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
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """DELETE FROM records WHERE id = %s RETURNING id;""",
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


def _json_bind_collection_4c6973745b426967446563696d616c5d(value: list[Decimal] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([str(item) for item in value])


def _read_collection_4c6973745b426967446563696d616c5d(row: tuple[object, ...], index: int) -> list[Decimal]:
    data = row[index]
    return [Decimal(str(item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b426967446563696d616c5d_col(row: dict[str, object], column: str) -> list[Decimal]:
    data = row[column]
    return [Decimal(str(item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b426c6f625d(value: list[bytes] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([item.hex() for item in value])


def _read_collection_4c6973745b426c6f625d(row: tuple[object, ...], index: int) -> list[bytes]:
    data = row[index]
    return [bytes.fromhex(cast(str, item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b426c6f625d_col(row: dict[str, object], column: str) -> list[bytes]:
    data = row[column]
    return [bytes.fromhex(cast(str, item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b4c6973745b54696d657374616d705d5d(
    value: list[list[datetime]] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([[_dump_json_timestamp(item) for item in item] for item in value])


def _read_collection_4c6973745b4c6973745b54696d657374616d705d5d(
    row: tuple[object, ...], index: int
) -> list[list[datetime]]:
    data = row[index]
    return [[_map_json_timestamp(item) for item in cast(list[object], item)] for item in cast(list[object], data)]


def _read_collection_4c6973745b4c6973745b54696d657374616d705d5d_col(
    row: dict[str, object], column: str
) -> list[list[datetime]]:
    data = row[column]
    return [[_map_json_timestamp(item) for item in cast(list[object], item)] for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b54696d657374616d705d(value: list[datetime] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([_dump_json_timestamp(item) for item in value])


def _read_collection_4c6973745b54696d657374616d705d(row: tuple[object, ...], index: int) -> list[datetime]:
    data = row[index]
    return [_map_json_timestamp(item) for item in cast(list[object], data)]


def _read_collection_4c6973745b54696d657374616d705d_col(row: dict[str, object], column: str) -> list[datetime]:
    data = row[column]
    return [_map_json_timestamp(item) for item in cast(list[object], data)]


def _json_bind_collection_4d61705b537472696e672c20426967446563696d616c5d(
    value: dict[str, Decimal] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb({key: str(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c20426967446563696d616c5d(
    row: tuple[object, ...], index: int
) -> dict[str, Decimal]:
    data = row[index]
    return {str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()}


def _read_collection_4d61705b537472696e672c20426967446563696d616c5d_col(
    row: dict[str, object], column: str
) -> dict[str, Decimal]:
    data = row[column]
    return {str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()}


def _json_bind_collection_4d61705b537472696e672c20426c6f625d(value: dict[str, bytes] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb({key: mapped_value.hex() for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c20426c6f625d(row: tuple[object, ...], index: int) -> dict[str, bytes]:
    data = row[index]
    return {
        str(key): bytes.fromhex(cast(str, mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c20426c6f625d_col(row: dict[str, object], column: str) -> dict[str, bytes]:
    data = row[column]
    return {
        str(key): bytes.fromhex(cast(str, mapped_value)) for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
    value: dict[str, dict[str, Decimal]] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb(
        {
            key: {key: str(mapped_value) for key, mapped_value in mapped_value.items()}
            for key, mapped_value in value.items()
        }
    )


def _read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d(
    row: tuple[object, ...], index: int
) -> dict[str, dict[str, Decimal]]:
    data = row[index]
    return {
        str(key): {
            str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], mapped_value).items()
        }
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204d61705b537472696e672c20426967446563696d616c5d5d_col(
    row: dict[str, object], column: str
) -> dict[str, dict[str, Decimal]]:
    data = row[column]
    return {
        str(key): {
            str(key): Decimal(str(mapped_value)) for key, mapped_value in cast(dict[str, object], mapped_value).items()
        }
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c2054696d657374616d705d(
    value: dict[str, datetime] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb({key: _dump_json_timestamp(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c2054696d657374616d705d(
    row: tuple[object, ...], index: int
) -> dict[str, datetime]:
    data = row[index]
    return {str(key): _map_json_timestamp(mapped_value) for key, mapped_value in cast(dict[str, object], data).items()}


def _read_collection_4d61705b537472696e672c2054696d657374616d705d_col(
    row: dict[str, object], column: str
) -> dict[str, datetime]:
    data = row[column]
    return {str(key): _map_json_timestamp(mapped_value) for key, mapped_value in cast(dict[str, object], data).items()}


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
