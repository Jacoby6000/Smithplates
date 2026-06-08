# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import sqlite3
from typing import cast
import aiosqlite
from sqlite_transaction_run import run
from bookmark_repository_models import (
    Bookmark,
)
from bookmark_repository_protocol import BookmarkRepositoryServiceProtocol


class BookmarkRepositoryAiosqliteService(BookmarkRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_bookmark(
        self,
        title: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO bookmarks (title) VALUES (?) RETURNING id;""",
                (title,),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)
        return await run(self._connection, transaction, execute)

def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
