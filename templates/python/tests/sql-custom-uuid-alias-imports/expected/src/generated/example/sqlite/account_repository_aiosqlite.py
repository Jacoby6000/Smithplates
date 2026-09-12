# Generated from example#AccountRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.example.account_repository_protocol import AccountRepositoryServiceProtocol
from generated.example.models.account_repository_models import (
    ExternalId,
    TenantId,
)
from generated.example.sqlite.sqlite_transaction_run import run


class AccountRepositoryAiosqliteService(AccountRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_account(
        self,
        tenant_id: TenantId | None,
        external_id: ExternalId | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO accounts (tenant_id, external_id) VALUES (?, ?) RETURNING id;""",
                (tenant_id, external_id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
