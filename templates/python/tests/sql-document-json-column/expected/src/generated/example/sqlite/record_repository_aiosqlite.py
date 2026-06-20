# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
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
    async def insert_record(
        self,
        metadata: object,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO records (metadata) VALUES (?) RETURNING id;""",
                (_json_bind_Document(metadata),),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record_by_id(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Record | None:
        async def execute(conn: aiosqlite.Connection) -> Record | None:
            cursor = await conn.execute(
                """SELECT records.id, records.metadata
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
                metadata=_read_Document_col(named_row, "metadata"),
            )

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


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _json_bind_Document(value: object) -> str:
    return json.dumps(value)


def _read_Document(row: tuple[object, ...] | sqlite3.Row, index: int) -> object:
    return cast(object, json.loads(_read_str(row, index)))


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])


def _read_Document_col(row: dict[str, object], column: str) -> object:
    return cast(object, json.loads(_read_str_col(row, column)))
