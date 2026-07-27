# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.flag_repository_protocol import FlagRepositoryServiceProtocol
from generated.example.models.flag_repository_models import (
    Flag,
)
from generated.example.postgres.psycopg_transaction_run import run
from psycopg.rows import class_row
from psycopg.types.string import TextLoader


class FlagRepositoryPsycopgService(FlagRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def create_flag(
        self,
        label: str | None,
        enabled: bool | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO flags (label, enabled) VALUES (%s, %s) RETURNING id;""",
                (label, enabled),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_flag(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Flag | None:
        async def execute() -> Flag | None:
            async with self._connection.cursor(row_factory=class_row(Flag)) as cur:
                await cur.execute(
                    """SELECT flags.id, flags.label, flags.enabled
FROM flags
WHERE id = %s;""",
                    (id,),
                )
                return await cur.fetchone()

        return await run(self._connection, transaction, execute)

    @override
    async def update_flag(
        self,
        label: str | None,
        enabled: bool | None,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE flags
SET label = %s, enabled = %s
WHERE id = %s;""",
                (label, enabled, id),
            )
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
