# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.task_repository_models import (
    Task,
)
from generated.example.task_priority import TaskPriority
from generated.example.task_status import TaskStatus

T = TypeVar("T", contravariant=True)


class TaskRepositoryServiceProtocol(Protocol[T]):
    async def create_task(
        self,
        label: str | None,
        status: TaskStatus | None,
        priority: TaskPriority | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_task(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Task | None: ...
