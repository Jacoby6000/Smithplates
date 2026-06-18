# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.db.models.shipment_repository_models import (
    CreateShipmentResult,
    DeleteShipmentOutput,
    DeliveryState,
    PostalAddress,
    Shipment,
    UpdateShipmentResult,
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
    ) -> CreateShipmentResult: ...
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
    ) -> UpdateShipmentResult: ...
    async def delete_shipment(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> DeleteShipmentOutput: ...
