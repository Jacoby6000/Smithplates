# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.model.order_repository_models import (
    OrderLine,
)


@dataclass
class GetOrderResult:
    id: str
    label: str
    order_lines: list[OrderLine]


T = TypeVar("T", contravariant=True)


class OrderRepositoryServiceProtocol(Protocol[T]):
    async def create_order(
        self,
        label: str,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_order(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetOrderResult | None: ...
