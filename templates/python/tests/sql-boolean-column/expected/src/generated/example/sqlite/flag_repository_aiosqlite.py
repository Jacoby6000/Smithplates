# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.example.flag_repository_protocol import FlagRepositoryServiceProtocol
from generated.example.models.flag_repository_models import (
    Flag,
)
from generated.example.sqlite.sqlite_transaction_run import run


class FlagRepositoryAiosqliteService(FlagRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_flag(
        self,
        label: str | None,
        enabled: bool | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO flags (label, enabled) VALUES (?, ?) RETURNING id;""",
                (label, enabled),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_flag(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Flag | None:
        async def execute(conn: aiosqlite.Connection) -> Flag | None:
            cursor = await conn.execute(
                """SELECT flags.id, flags.label, flags.enabled
FROM flags
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _Flag_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)

    @override
    async def update_flag(
        self,
        label: str | None,
        enabled: bool | None,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE flags
SET label = ?, enabled = ?
WHERE id = ?;""",
                (label, enabled, id),
            )
            return cursor.rowcount > 0

        return await run(self._connection, transaction, execute)


def _Flag_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> Flag:
    return Flag(
        id=_read_str(row, 0),
        label=None if row[1] is None else _read_str(row, 1),
        enabled=None if row[2] is None else _read_bool(row, 2),
    )


def _read_bool(row: tuple[object, ...] | sqlite3.Row, index: int) -> bool:
    value = row[index]
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"0", "false", "no", "off"}:
            return False
        if normalized in {"1", "true", "yes", "on"}:
            return True
        return bool(normalized)
    return bool(value)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
