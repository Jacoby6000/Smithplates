# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.record_repository_models import (
    Record,
)

T = TypeVar("T", contravariant=True)


class RecordRepositoryServiceProtocol(Protocol[T]):
    async def insert_record(
        self,
        metadata: object,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_record_by_id(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Record | None: ...
