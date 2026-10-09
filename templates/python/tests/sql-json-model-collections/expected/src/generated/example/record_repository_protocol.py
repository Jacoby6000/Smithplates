# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.record_repository_models import (
    Branch,
    Choice,
    Leaf,
    Record,
)

T = TypeVar("T", contravariant=True)


class RecordRepositoryServiceProtocol(Protocol[T]):
    async def create_record(
        self,
        leaves: dict[str, Leaf],
        choices: list[Choice],
        groups: dict[str, list[Branch]] | None,
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
        leaves: dict[str, Leaf],
        choices: list[Choice],
        groups: dict[str, list[Branch]] | None,
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
