# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class DeliveryStatePending:
    pending: str


@dataclass
class DeliveryStateDelivered:
    delivered: datetime


DeliveryState = DeliveryStatePending | DeliveryStateDelivered


@dataclass
class Shipment:
    id: str
    label: str
    destination: PostalAddress
    state: DeliveryState
    created_at: datetime


@dataclass
class ShipmentNotFound:
    message: str


@dataclass
class PostalAddress:
    street: str
    city: str
