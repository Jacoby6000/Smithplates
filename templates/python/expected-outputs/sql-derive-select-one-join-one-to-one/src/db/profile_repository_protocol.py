# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from profile_repository_models import (
    Bar,
    Profile,
    GetProfileResult,
)

T = TypeVar("T", contravariant=True)


class ProfileRepositoryServiceProtocol(Protocol[T]):
    async def get_profile(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetProfileResult | None:
        ...
