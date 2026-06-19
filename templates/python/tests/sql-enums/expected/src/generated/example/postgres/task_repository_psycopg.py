# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.models.task_repository_models import (
    Task,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.task_priority import TaskPriority
from generated.example.task_repository_protocol import TaskRepositoryServiceProtocol
from generated.example.task_status import TaskStatus
from psycopg.rows import class_row
from psycopg.types.string import TextLoader


class TaskRepositoryPsycopgService(TaskRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def create_task(
        self,
        id: str,
        label: str | None,
        status: TaskStatus | None,
        priority: TaskPriority | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO tasks (id, label, status, priority) VALUES (%s, %s, %s, %s) RETURNING id;""",
                (id, label, status, priority),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_task(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Task | None:
        async def execute() -> Task | None:
            async with self._connection.cursor(row_factory=class_row(Task)) as cur:
                await cur.execute(
                    """SELECT tasks.id, tasks.label, tasks.status, tasks.priority
FROM tasks
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
