# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypedDict, TypeVar

from generated.example.models.department_repository_models import (
    Category,
    Widget,
)


class GetDepartmentResult(TypedDict):
    id: str
    name: str | None
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
