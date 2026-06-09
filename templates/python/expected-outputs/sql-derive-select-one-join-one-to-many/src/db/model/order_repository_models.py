# Generated from example#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Order:
    id: str
    label: str | None

@dataclass
class OrderLine:
    id: str
    order_id: str | None
    sku: str | None

@dataclass
class GetOrderResult:
    id: str
    label: str
    order_lines: list[OrderLine]
