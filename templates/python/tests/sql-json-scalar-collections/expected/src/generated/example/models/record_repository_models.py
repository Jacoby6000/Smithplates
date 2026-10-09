# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal


@dataclass
class Record:
    id: str
    instants: list[datetime]
    instant_map: dict[str, datetime]
    amounts: list[Decimal]
    amount_map: dict[str, Decimal]
    payloads: list[bytes]
    payload_map: dict[str, bytes]
    instant_groups: list[list[datetime]]
    nested_amounts: dict[str, dict[str, Decimal]]
    optional_instants: list[datetime] | None
