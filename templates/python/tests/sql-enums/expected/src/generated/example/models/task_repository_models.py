# Generated from example#TaskRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass

from generated.example.task_priority import TaskPriority
from generated.example.task_status import TaskStatus


@dataclass
class Task:
    id: str
    label: str | None
    status: TaskStatus | None
    priority: TaskPriority | None
