# Generated from petstore#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from category_repository_models import (
    Store,
)
from category_repository_protocol import (
    CategoryRepositoryServiceProtocol,
    GetCategoryRecordResult,
)
from psycopg_transaction_run import run


class CategoryRepositoryPsycopgService(CategoryRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_category_record(
        self,
        name: str,
        store_id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
"""INSERT INTO categories (name, store_id) VALUES (%s, %s) RETURNING id;"""
,
                (name, store_id),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)
        return await run(self._connection, transaction, execute)
    @override
    async def get_category_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetCategoryRecordResult | None:
        async def execute() -> GetCategoryRecordResult | None:
            cur = await self._connection.execute(
"""SELECT categories.id, categories.name, categories.store_id, s.id AS s_id, s.name AS s_name
FROM categories AS categories
INNER JOIN stores AS s ON categories.store_id = s.id
WHERE categories.id = %s;"""
,
                (id,),
            )
            row = await cur.fetchone()
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
def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
