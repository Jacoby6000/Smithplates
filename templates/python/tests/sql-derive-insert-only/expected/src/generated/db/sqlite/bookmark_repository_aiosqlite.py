# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.bookmark_repository_protocol import BookmarkRepositoryServiceProtocol
from generated.db.models.bookmark_repository_models import (
    CreateBookmarkResult,
)
from generated.db.sqlite.sqlite_transaction_run import run


class BookmarkRepositoryAiosqliteService(BookmarkRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_bookmark(
        self,
        title: str | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateBookmarkResult:
        async def execute(conn: aiosqlite.Connection) -> CreateBookmarkResult:
            cursor = await conn.execute(
                """INSERT INTO bookmarks (title) VALUES (?) RETURNING id;""",
                (title,),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateBookmarkResult(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)


def _as_sqlite_named_row(
    cursor: sqlite3.Cursor | aiosqlite.Cursor,
    row: tuple[object, ...] | sqlite3.Row,
) -> dict[str, object]:
    if type(row) is not tuple:
        named = cast(sqlite3.Row, row)
        return {key: cast(object, named[key]) for key in named}
    description = cast(tuple[tuple[object, ...], ...], cursor.description)
    return {cast(str, column[0]): row[index] for index, column in enumerate(description)}


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
