# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.flag_repository_models import (
    Flag,
)

T = TypeVar("T", contravariant=True)


class FlagRepositoryServiceProtocol(Protocol[T]):
    async def create_flag(
        self,
        label: str | None,
        enabled: bool | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_flag(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Flag | None: ...
    async def update_flag(
        self,
        label: str | None,
        enabled: bool | None,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
