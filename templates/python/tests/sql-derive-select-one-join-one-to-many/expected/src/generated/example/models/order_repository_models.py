# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import TypedDict


class Order(TypedDict):
    id: str
    label: str | None


class OrderLine(TypedDict):
    id: str
    order_id: str | None
    sku: str | None
