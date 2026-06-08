# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

from psycopg.rows import class_row
from psycopg.types.string import TextLoader
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
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def get_bookmark(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Bookmark | None:
        async def execute() -> Bookmark | None:
            async with self._connection.cursor(row_factory=class_row(Bookmark)) as cur:
                await cur.execute(
                    """SELECT id, title FROM bookmarks WHERE id = %s;""",
                    (id,),
                )
                return await cur.fetchone()
        return await run(self._connection, transaction, execute)
