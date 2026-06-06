# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol

from widget_repository_models import (
    Widget,
)

class WidgetRepositoryServiceProtocol(Protocol):
    async def create_widget(
        self,
        foo: str,
        bar: int,
    ) -> str:
        ...
    async def get_widget(
        self,
        id: str,
    ) -> Widget | None:
        ...
    async def update_widget(
        self,
        foo: str,
        bar: int,
        id: str,
    ) -> bool:
        ...
    async def delete_widget(
        self,
        id: str,
    ) -> bool:
        ...
