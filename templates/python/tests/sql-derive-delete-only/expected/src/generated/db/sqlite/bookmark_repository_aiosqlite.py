# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import aiosqlite
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    DeleteBookmarkOutput,
)
from generated.db.sqlite.sqlite_transaction_run import run


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
    ) -> DeleteBookmarkOutput:
        async def execute(conn: aiosqlite.Connection) -> DeleteBookmarkOutput:
            cursor = await conn.execute(
                """DELETE FROM bookmarks WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return DeleteBookmarkOutput(deleted=row is not None)

        return await run(self._connection, transaction, execute)
