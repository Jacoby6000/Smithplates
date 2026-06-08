# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import psycopg
from psycopg_transaction_run import run
from bookmark_repository_models import (
    Bookmark,
)
from bookmark_repository_protocol import BookmarkRepositoryServiceProtocol


class BookmarkRepositoryPsycopgService(BookmarkRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def update_bookmark(
        self,
        title: str,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE bookmarks
SET title = %s
WHERE id = %s;""",
                (title, id),
            )
            return cur.rowcount > 0
        return await run(self._connection, transaction, execute)
