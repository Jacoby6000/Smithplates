# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    UpdateBookmarkResult,
)
from generated.db.sqlite.sqlite_transaction_run import run


class BookmarkRepositoryAiosqliteService(BookmarkRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def update_bookmark(
        self,
        title: str | None,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> UpdateBookmarkResult:
        async def execute(conn: aiosqlite.Connection) -> UpdateBookmarkResult:
            cursor = await conn.execute(
                """UPDATE bookmarks
SET title = ?
WHERE id = ? RETURNING id;""",
                (title, id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return UpdateBookmarkResult(
                id=_read_str(row, 0),
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
