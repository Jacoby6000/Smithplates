# Generated from example#ShipmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from datetime import datetime
from typing import TypedDict


class DeliveryStatePending(TypedDict):
    pending: str


class DeliveryStateDelivered(TypedDict):
    delivered: datetime


DeliveryState = DeliveryStatePending | DeliveryStateDelivered


class PostalAddress(TypedDict):
    street: str
    city: str


class Shipment(TypedDict):
    id: str
    label: str
    destination: PostalAddress
    state: DeliveryState
    created_at: datetime
