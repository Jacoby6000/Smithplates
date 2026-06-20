# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from typing import cast, override

import psycopg
from generated.example.models.record_repository_models import (
    Record,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from psycopg.rows import dict_row


class RecordRepositoryPsycopgService(RecordRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def insert_record(
        self,
        metadata: object,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO records (metadata) VALUES (%s) RETURNING id;""",
                (_json_bind_Document(metadata),),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record_by_id(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Record | None:
        async def execute() -> Record | None:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """SELECT records.id, records.metadata
FROM records
WHERE id = %s;""",
                    (id,),
                )
                row = await cur.fetchone()
                if row is None:
                    return None
                return Record(
                    id=_read_str_col(row, "id"),
                    metadata=_read_Document_col(row, "metadata"),
                )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_Document(value: object) -> str:
    return json.dumps(value)


def _read_Document(row: tuple[object, ...], index: int) -> object:
    value = row[index]
    if isinstance(value, str):
        return json.loads(value)
    return value


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_Document_col(row: dict[str, object], column: str) -> object:
    value = row[column]
    if isinstance(value, str):
        return json.loads(value)
    return value
