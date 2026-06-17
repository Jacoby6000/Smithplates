# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.models.department_repository_models import (
    Category,
    Widget,
)


@dataclass
class GetDepartmentResult:
    id: str
    name: str
    categories: list[Category]
    widgets: list[Widget]


T = TypeVar("T", contravariant=True)


class DepartmentRepositoryServiceProtocol(Protocol[T]):
    async def get_department(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetDepartmentResult | None: ...
