# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol

from shipment_repository_models import (
    PostalAddress,
    Shipment,
    ShipmentNotFound,
    DeliveryState,
)

class ShipmentRepositoryServiceProtocol(Protocol):
    async def create_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
    ) -> str:
        ...
    async def get_shipment(
        self,
        id: str,
    ) -> Shipment | ShipmentNotFound:
        ...
    async def update_shipment(
        self,
        label: str,
        destination: PostalAddress,
        state: DeliveryState,
        id: str,
    ) -> bool:
        ...
    async def delete_shipment(
        self,
        id: str,
    ) -> bool:
        ...
