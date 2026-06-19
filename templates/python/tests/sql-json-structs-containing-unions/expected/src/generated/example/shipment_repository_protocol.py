# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.shipment_repository_models import (
    DeliveryState,
    PostalAddress,
    Shipment,
)

T = TypeVar("T", contravariant=True)


class ShipmentRepositoryServiceProtocol(Protocol[T]):
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_shipment(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Shipment | None: ...
    async def update_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
    async def delete_shipment(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
