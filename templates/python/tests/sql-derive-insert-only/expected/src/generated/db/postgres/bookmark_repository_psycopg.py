# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    CreateBookmarkResult,
)
from generated.db.postgres.psycopg_transaction_run import run
from psycopg.rows import dict_row


class BookmarkRepositoryPsycopgService(BookmarkRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_bookmark(
        self,
        title: str | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> CreateBookmarkResult:
        async def execute() -> CreateBookmarkResult:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """INSERT INTO bookmarks (title) VALUES (%s) RETURNING id;""",
                    (title,),
                )
                row = await cur.fetchone()
                if row is None:
                    raise RuntimeError("INSERT RETURNING produced no row")
                return CreateBookmarkResult(
                    id=_read_str_col(row, "id"),
                )

        return await run(self._connection, transaction, execute)


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
