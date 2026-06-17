# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.models.widget_repository_models import (
    Category,
)


@dataclass
class GetWidgetResult:
    id: str
    title: str | None
    category_id: str | None
    category: Category | None


T = TypeVar("T", contravariant=True)


class WidgetRepositoryServiceProtocol(Protocol[T]):
    async def get_widget(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetWidgetResult | None: ...
