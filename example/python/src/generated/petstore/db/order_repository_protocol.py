# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.petstore.db.models.order_repository_models import (
    CreateOrderRecordOutput,
    OrderLine,
)
from generated.petstore.db.order_priority import OrderPriority
from generated.petstore.db.order_status import OrderStatus


@dataclass
class GetOrderRecordResult:
    id: str
    label: str
    status: OrderStatus
    priority: OrderPriority
    created_at: datetime
    updated_at: datetime
    order_lines: list[OrderLine]


T = TypeVar("T", contravariant=True)


class OrderRepositoryServiceProtocol(Protocol[T]):
    async def create_order_record(
        self,
        label: str,
        status: OrderStatus,
        priority: OrderPriority,
        *,
        transaction: T | None = None,
    ) -> CreateOrderRecordOutput: ...
    async def get_order_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetOrderRecordResult | None: ...
