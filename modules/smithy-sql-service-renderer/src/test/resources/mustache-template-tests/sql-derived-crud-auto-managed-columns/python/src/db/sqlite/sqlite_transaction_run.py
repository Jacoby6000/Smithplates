# Bundled sqlite transaction helper for sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import TypeVar

import sqlite3
import aiosqlite

T = TypeVar("T")
SqliteRow = tuple[object, ...] | sqlite3.Row


@asynccontextmanager
async def _use_sqlite_transaction(
    connection: aiosqlite.Connection,
) -> AsyncIterator[aiosqlite.Connection]:
    await connection.execute("BEGIN")
    try:
        yield connection
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise


async def run(
    connection: aiosqlite.Connection,
    transaction: aiosqlite.Connection | None,
    execute: Callable[[aiosqlite.Connection], Awaitable[T]],
) -> T:
    if transaction is None:
        async with _use_sqlite_transaction(connection) as conn:
            return await execute(conn)
    return await execute(transaction)


async def insert_returning(
    conn: aiosqlite.Connection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[SqliteRow], T],
) -> T:
    cursor = await conn.execute(sql, params)
    row = await cursor.fetchone()
    if row is None:
        raise RuntimeError("INSERT RETURNING produced no row")
    return map_row(row)


async def insert_returning_row(
    conn: aiosqlite.Connection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[object, SqliteRow], T],
) -> T:
    cursor = await conn.execute(sql, params)
    row = await cursor.fetchone()
    if row is None:
        raise RuntimeError("INSERT RETURNING produced no row")
    return map_row(cursor, row)


async def select_one(
    conn: aiosqlite.Connection,
    sql: str,
    params: tuple[object, ...],
    map_row: Callable[[object, SqliteRow], T],
) -> T | None:
    cursor = await conn.execute(sql, params)
    row = await cursor.fetchone()
    if row is None:
        return None
    return map_row(cursor, row)


async def mutate_bool(
    conn: aiosqlite.Connection,
    sql: str,
    params: tuple[object, ...],
    *,
    by_rowcount: bool,
) -> bool:
    cursor = await conn.execute(sql, params)
    if by_rowcount:
        return cursor.rowcount > 0
    row = await cursor.fetchone()
    return row is not None
