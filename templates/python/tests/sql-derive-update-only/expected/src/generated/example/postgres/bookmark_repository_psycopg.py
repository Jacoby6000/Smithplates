# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import psycopg
from generated.example.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.example.postgres.psycopg_transaction_run import run


class BookmarkRepositoryPsycopgService(BookmarkRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def update_bookmark(
        self,
        title: str | None,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE bookmarks
SET title = %s
WHERE id = %s;""",
                (None if title is None else title, id),
            )
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)
