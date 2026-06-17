# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.category_repository_protocol import CategoryRepositoryServiceProtocol
from generated.db.models.category_repository_models import (
    CategoryItem,
)
from generated.db.sqlite.sqlite_transaction_run import run


class CategoryRepositoryAiosqliteService(CategoryRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_category(
        self,
        name: str,
        parent_category_id: str,
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
    async def get_category_item(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CategoryItem | None:
        async def execute(conn: aiosqlite.Connection) -> CategoryItem | None:
            cursor = await conn.execute(
                """SELECT category_items.id, category_items.category_id, category_items.label
FROM category_items
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _CategoryItem_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)


def _CategoryItem_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> CategoryItem:
    return CategoryItem(
        id=_read_str(row, 0),
        category_id=_read_str(row, 1),
        label=_read_str(row, 2),
    )


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
