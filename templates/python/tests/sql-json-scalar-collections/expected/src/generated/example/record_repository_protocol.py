# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Protocol, TypeVar

from generated.example.models.record_repository_models import (
    Record,
)

T = TypeVar("T", contravariant=True)


class RecordRepositoryServiceProtocol(Protocol[T]):
    async def create_record(
        self,
        instants: list[datetime],
        instant_map: dict[str, datetime],
        amounts: list[Decimal],
        amount_map: dict[str, Decimal],
        payloads: list[bytes],
        payload_map: dict[str, bytes],
        instant_groups: list[list[datetime]],
        nested_amounts: dict[str, dict[str, Decimal]],
        optional_instants: list[datetime] | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Record | None: ...
    async def update_record(
        self,
        instants: list[datetime],
        instant_map: dict[str, datetime],
        amounts: list[Decimal],
        amount_map: dict[str, Decimal],
        payloads: list[bytes],
        payload_map: dict[str, bytes],
        instant_groups: list[list[datetime]],
        nested_amounts: dict[str, dict[str, Decimal]],
        optional_instants: list[datetime] | None,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
    async def delete_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
