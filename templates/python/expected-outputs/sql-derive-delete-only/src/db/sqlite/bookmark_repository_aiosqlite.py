# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

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
    async def delete_bookmark(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """DELETE FROM bookmarks WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return row is not None
        return await run(self._connection, transaction, execute)
