# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.example.models.task_repository_models import (
    Task,
)
from generated.example.sqlite.sqlite_transaction_run import run
from generated.example.task_priority import TaskPriority
from generated.example.task_repository_protocol import TaskRepositoryServiceProtocol
from generated.example.task_status import TaskStatus


class TaskRepositoryAiosqliteService(TaskRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_task(
        self,
        id: str,
        label: str | None,
        status: TaskStatus | None,
        priority: TaskPriority | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO tasks (id, label, status, priority) VALUES (?, ?, ?, ?) RETURNING id;""",
                (id, label, status, priority),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_task(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Task | None:
        async def execute(conn: aiosqlite.Connection) -> Task | None:
            cursor = await conn.execute(
                """SELECT tasks.id, tasks.label, tasks.status, tasks.priority
FROM tasks
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _Task_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)


def _Task_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> Task:
    return Task(
        id=_read_str(row, 0),
        label=None if row[1] is None else _read_str(row, 1),
        status=None if row[2] is None else TaskStatus(_read_str(row, 2)),
        priority=None if row[3] is None else TaskPriority(_read_int(row, 3)),
    )


def _read_int(row: tuple[object, ...] | sqlite3.Row, index: int) -> int:
    return cast(int, row[index])


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])
