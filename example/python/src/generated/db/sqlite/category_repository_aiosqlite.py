# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite

from generated.db.category_repository_protocol import (
    CategoryRepositoryServiceProtocol,
    GetCategoryRecordResult,
)
from generated.db.models.category_repository_models import (
    CreateCategoryRecordOutput,
    Store,
)
from generated.db.sqlite.sqlite_transaction_run import run


class CategoryRepositoryAiosqliteService(CategoryRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_category_record(
        self,
        name: str,
        store_id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateCategoryRecordOutput:
        async def execute(conn: aiosqlite.Connection) -> CreateCategoryRecordOutput:
            cursor = await conn.execute(
                """INSERT INTO categories (name, store_id) VALUES (?, ?) RETURNING id;""",
                (name, store_id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateCategoryRecordOutput(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_category_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetCategoryRecordResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetCategoryRecordResult | None:
            cursor = await conn.execute(
                """SELECT categories.id, categories.name, categories.store_id, s.id AS s_id, s.name AS s_name
FROM categories AS categories
INNER JOIN stores AS s ON categories.store_id = s.id
WHERE categories.id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return GetCategoryRecordResult(
                id=_read_str(row, 0),
                name=_read_str(row, 1),
                store_id=_read_str(row, 2),
                store=Store(
                    id=_read_str(row, 3),
                    name=_read_str(row, 4),
                ),
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


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
