# Bundled postgres transaction helper for sql-service-codegen. Do not edit by hand.
# pyright: reportUnnecessaryTypeIgnoreComment=false
from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import TypeVar

import psycopg
from psycopg.rows import class_row, dict_row

T = TypeVar("T")


async def run(
    connection: psycopg.AsyncConnection,
    transaction: psycopg.AsyncTransaction | None,
    execute: Callable[[], Awaitable[T]],
) -> T:
    if transaction is None:
        async with connection.transaction():
            return await execute()
    return await execute()


async def insert_returning(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[tuple[object, ...]], T],
) -> T:
    cur = await connection.execute(sql, params)  # pyright: ignore[reportArgumentType]
    row = await cur.fetchone()
    if row is None:
        raise RuntimeError("INSERT RETURNING produced no row")
    return map_row(row)


async def insert_returning_dict_row(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[dict[str, object]], T],
) -> T:
    async with connection.cursor(row_factory=dict_row) as cur:  # type: ignore[misc]
        _ = await cur.execute(sql, params)  # pyright: ignore[reportArgumentType]
        row = await cur.fetchone()
        if row is None:
            raise RuntimeError("INSERT RETURNING produced no row")
        return map_row(row)


async def select_one_class_row(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    row_type: type[T],
) -> T | None:
    async with connection.cursor(row_factory=class_row(row_type)) as cur:
        _ = await cur.execute(sql, params)  # pyright: ignore[reportArgumentType]
        return await cur.fetchone()


async def select_one_dict_row(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[dict[str, object]], T],
) -> T | None:
    async with connection.cursor(row_factory=dict_row) as cur:  # type: ignore[misc]
        _ = await cur.execute(sql, params)  # pyright: ignore[reportArgumentType]
        row = await cur.fetchone()
        if row is None:
            return None
        return map_row(row)


async def select_one_tuple(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[tuple[object, ...]], T],
) -> T | None:
    cur = await connection.execute(sql, params)  # pyright: ignore[reportArgumentType]
    row = await cur.fetchone()
    if row is None:
        return None
    return map_row(row)


async def mutate_bool(
    connection: psycopg.AsyncConnection,
    sql: str,
    params: tuple[object, ...],
    *,
    by_rowcount: bool,
) -> bool:
    cur = await connection.execute(sql, params)  # pyright: ignore[reportArgumentType]
    if by_rowcount:
        return cur.rowcount > 0
    row = await cur.fetchone()
    return row is not None
