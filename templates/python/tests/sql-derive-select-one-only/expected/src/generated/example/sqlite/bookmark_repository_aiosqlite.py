# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.example.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.example.models.bookmark_repository_models import (
    Bookmark,
)
from generated.example.sqlite.sqlite_transaction_run import run


class BookmarkRepositoryAiosqliteService(BookmarkRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_bookmark(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Bookmark | None:
        async def execute(conn: aiosqlite.Connection) -> Bookmark | None:
            cursor = await conn.execute(
                """SELECT bookmarks.id, bookmarks.title
FROM bookmarks
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _Bookmark_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)


def _Bookmark_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> Bookmark:
    return Bookmark(
        id=_read_str(row, 0),
        title=None if row[1] is None else _read_str(row, 1),
    )


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
