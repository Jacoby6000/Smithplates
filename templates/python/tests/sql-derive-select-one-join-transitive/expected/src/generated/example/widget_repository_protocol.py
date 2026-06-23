# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypedDict, TypeVar

from generated.example.models.widget_repository_models import (
    Category,
    Department,
)


class GetWidgetResult(TypedDict):
    id: str
    title: str | None
    category_id: str
    category: Category
    department: Department


T = TypeVar("T", contravariant=True)


class WidgetRepositoryServiceProtocol(Protocol[T]):
    async def get_widget(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetWidgetResult | None: ...
