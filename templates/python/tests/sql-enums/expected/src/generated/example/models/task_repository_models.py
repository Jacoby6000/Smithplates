# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import TypedDict

from generated.example.task_priority import TaskPriority
from generated.example.task_status import TaskStatus


class Task(TypedDict):
    id: str
    label: str | None
    status: TaskStatus | None
    priority: TaskPriority | None
