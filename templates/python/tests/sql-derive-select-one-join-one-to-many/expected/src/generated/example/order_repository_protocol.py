# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypedDict, TypeVar

from generated.example.models.order_repository_models import (
    OrderLine,
)


class GetOrderResult(TypedDict):
    id: str
    label: str | None
    order_lines: list[OrderLine]


T = TypeVar("T", contravariant=True)


class OrderRepositoryServiceProtocol(Protocol[T]):
    async def create_order(
        self,
        label: str | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_order(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetOrderResult | None: ...
