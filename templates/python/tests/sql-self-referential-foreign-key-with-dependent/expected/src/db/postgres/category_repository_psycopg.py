# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.db.category_repository_protocol import CategoryRepositoryServiceProtocol
from generated.db.models.category_repository_models import (
    CategoryItem,
)
from generated.db.postgres.psycopg_transaction_run import run
from psycopg.rows import class_row
from psycopg.types.string import TextLoader


class CategoryRepositoryPsycopgService(CategoryRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def create_category(
        self,
        name: str,
        parent_category_id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO categories (name, parent_category_id) VALUES (%s, %s) RETURNING id;""",
                (name, parent_category_id),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_category_item(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> CategoryItem | None:
        async def execute() -> CategoryItem | None:
            async with self._connection.cursor(row_factory=class_row(CategoryItem)) as cur:
                await cur.execute(
                    """SELECT category_items.id, category_items.category_id, category_items.label
FROM category_items
WHERE id = %s;""",
                    (id,),
                )
                return await cur.fetchone()

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
