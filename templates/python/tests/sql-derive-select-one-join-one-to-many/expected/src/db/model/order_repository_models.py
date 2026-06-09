# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Order:
    id: str
    label: str | None


@dataclass
class OrderLine:
    id: str
    order_id: str | None
    sku: str | None
