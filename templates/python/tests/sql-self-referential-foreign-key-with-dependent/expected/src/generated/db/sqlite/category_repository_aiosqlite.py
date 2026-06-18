# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.category_repository_protocol import CategoryRepositoryServiceProtocol
from generated.db.models.category_repository_models import (
    Category,
    CreateCategoryResult,
)
from generated.db.sqlite.sqlite_transaction_run import run


class CategoryRepositoryAiosqliteService(CategoryRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_category(
        self,
        name: str | None,
        parent_category_id: str | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateCategoryResult:
        async def execute(conn: aiosqlite.Connection) -> CreateCategoryResult:
            cursor = await conn.execute(
                """INSERT INTO categories (name, parent_category_id) VALUES (?, ?) RETURNING id;""",
                (name, parent_category_id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateCategoryResult(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_category(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Category | None:
        async def execute(conn: aiosqlite.Connection) -> Category | None:
            cursor = await conn.execute(
                """SELECT categories.id, categories.name, categories.parent_category_id
FROM categories
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _Category_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)


def _Category_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> Category:
    return Category(
        id=_read_str(row, 0),
        name=_read_str(row, 1),
        parent_category_id=_read_str(row, 2),
    )


def _as_sqlite_named_row(
    cursor: sqlite3.Cursor | aiosqlite.Cursor,
    row: tuple[object, ...] | sqlite3.Row,
) -> dict[str, object]:
    if type(row) is not tuple:
        named = cast(sqlite3.Row, row)
        return {key: cast(object, named[key]) for key in named}
    description = cast(tuple[tuple[object, ...], ...], cursor.description)
    return {cast(str, column[0]): row[index] for index, column in enumerate(description)}


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
