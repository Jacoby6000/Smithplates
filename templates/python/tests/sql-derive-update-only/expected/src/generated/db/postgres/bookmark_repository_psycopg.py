# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    UpdateBookmarkResult,
)
from generated.db.postgres.psycopg_transaction_run import run


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
    ) -> UpdateBookmarkResult:
        async def execute() -> UpdateBookmarkResult:
            cur = await self._connection.execute(
                """UPDATE bookmarks
SET title = %s
WHERE id = %s RETURNING id;""",
                (title, id),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return UpdateBookmarkResult(
                id=_read_str(row, 0),
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
