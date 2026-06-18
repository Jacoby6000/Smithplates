# Bundled postgres transaction helper for sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import TypeVar

import psycopg

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
