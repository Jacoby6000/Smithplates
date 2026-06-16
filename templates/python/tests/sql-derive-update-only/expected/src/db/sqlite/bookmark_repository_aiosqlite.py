# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import aiosqlite
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.sqlite.sqlite_transaction_run import run


class BookmarkRepositoryAiosqliteService(BookmarkRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def update_bookmark(
        self,
        title: str,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE bookmarks
SET title = ?
WHERE id = ?;""",
                (title, id),
            )
            return cursor.rowcount > 0

        return await run(self._connection, transaction, execute)
