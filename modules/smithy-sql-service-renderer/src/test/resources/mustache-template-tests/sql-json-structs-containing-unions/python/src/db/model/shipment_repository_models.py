# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import TypedDict
class DeliveryStatePending(TypedDict):
    pending: str
class DeliveryStateDelivered(TypedDict):
    delivered: datetime
DeliveryState = DeliveryStatePending | DeliveryStateDelivered

@dataclass
class PostalAddress:
    street: str
    city: str
@dataclass
class Shipment:
    id: str
    label: str
    destination: PostalAddress
    state: DeliveryState
    created_at: datetime
