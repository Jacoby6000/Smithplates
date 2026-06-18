# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.category_repository_protocol import CategoryRepositoryServiceProtocol
from generated.db.models.category_repository_models import (
    Category,
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
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO categories (name, parent_category_id) VALUES (?, ?) RETURNING id;""",
                (name, parent_category_id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

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
        name=None if row[1] is None else _read_str(row, 1),
        parent_category_id=None if row[2] is None else _read_str(row, 2),
    )


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
