# Bundled sqlite transaction helper for sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import TypeVar

import aiosqlite

T = TypeVar("T")


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
