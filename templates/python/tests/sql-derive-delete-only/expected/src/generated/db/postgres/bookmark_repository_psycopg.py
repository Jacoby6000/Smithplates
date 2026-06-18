# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

import psycopg
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    DeleteBookmarkOutput,
)
from generated.db.postgres.psycopg_transaction_run import run


class BookmarkRepositoryPsycopgService(BookmarkRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def delete_bookmark(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> DeleteBookmarkOutput:
        async def execute() -> DeleteBookmarkOutput:
            cur = await self._connection.execute(
                """DELETE FROM bookmarks WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
            return DeleteBookmarkOutput(deleted=row is not None)

        return await run(self._connection, transaction, execute)
