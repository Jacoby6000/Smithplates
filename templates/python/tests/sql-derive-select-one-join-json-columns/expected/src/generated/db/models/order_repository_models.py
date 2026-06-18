# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import TypedDict


class FulfillmentStatePending(TypedDict):
    pending: str


class FulfillmentStateShipped(TypedDict):
    shipped: datetime


class FulfillmentStateDelivered(TypedDict):
    delivered: datetime


FulfillmentState = FulfillmentStatePending | FulfillmentStateShipped | FulfillmentStateDelivered


@dataclass
class Order:
    id: str
    label: str


@dataclass
class OrderLine:
    id: str
    order_id: str
    sku: str
    fulfillment: FulfillmentState
    ship_to: PostalAddress


@dataclass
class PostalAddress:
    street: str
    city: str


@dataclass
class CreateOrderResult:
    id: str
