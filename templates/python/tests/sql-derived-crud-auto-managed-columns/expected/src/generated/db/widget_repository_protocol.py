# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.db.models.widget_repository_models import (
    CreateWidgetResult,
    DeleteWidgetOutput,
    UpdateWidgetResult,
    Widget,
)

T = TypeVar("T", contravariant=True)


class WidgetRepositoryServiceProtocol(Protocol[T]):
    async def create_widget(
        self,
        foo: str | None,
        bar: int | None,
        *,
        transaction: T | None = None,
    ) -> CreateWidgetResult: ...
    async def get_widget(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Widget | None: ...
    async def update_widget(
        self,
        foo: str | None,
        bar: int | None,
        id: str,
        *,
        transaction: T | None = None,
    ) -> UpdateWidgetResult: ...
    async def delete_widget(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> DeleteWidgetOutput: ...
