# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.model.widget_repository_models import (
    Category,
    Department,
)


@dataclass
class GetWidgetResult:
    id: str
    title: str
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
