# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from datetime import datetime
from typing import TypedDict


class FulfillmentStatePending(TypedDict):
    pending: str


class FulfillmentStateShipped(TypedDict):
    shipped: datetime


class FulfillmentStateDelivered(TypedDict):
    delivered: datetime


FulfillmentState = FulfillmentStatePending | FulfillmentStateShipped | FulfillmentStateDelivered


class Order(TypedDict):
    id: str
    label: str


class OrderLine(TypedDict):
    id: str
    order_id: str
    sku: str
    fulfillment: FulfillmentState
    ship_to: PostalAddress


class PostalAddress(TypedDict):
    street: str
    city: str
