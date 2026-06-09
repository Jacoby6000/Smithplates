# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from widget_repository_models import (
    Category,
    Widget,
    GetWidgetResult,
)

T = TypeVar("T", contravariant=True)


class WidgetRepositoryServiceProtocol(Protocol[T]):
    async def get_widget(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetWidgetResult | None:
        ...
