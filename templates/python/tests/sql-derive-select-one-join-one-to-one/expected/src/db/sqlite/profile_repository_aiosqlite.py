# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.models.profile_repository_models import (
    Bar,
)
from generated.db.profile_repository_protocol import (
    GetProfileResult,
    ProfileRepositoryServiceProtocol,
)
from generated.db.sqlite.sqlite_transaction_run import run


class ProfileRepositoryAiosqliteService(ProfileRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_profile(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetProfileResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetProfileResult | None:
            cursor = await conn.execute(
                """SELECT profiles.id, profiles.display_name, profiles.bar_id, b.id AS b_id, b.name AS b_name
FROM profiles AS profiles
INNER JOIN bars AS b ON profiles.bar_id = b.id
WHERE profiles.id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return GetProfileResult(
                id=_read_str(row, 0),
                display_name=None if row[1] is None else _read_str(row, 1),
                bar_id=_read_str(row, 2),
                bar=Bar(
                    id=_read_str(row, 3),
                    name=None if row[4] is None else _read_str(row, 4),
                ),
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
