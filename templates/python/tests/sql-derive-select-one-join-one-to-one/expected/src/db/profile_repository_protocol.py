# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.models.profile_repository_models import (
    Bar,
)


@dataclass
class GetProfileResult:
    id: str
    display_name: str | None
    bar_id: str
    bar: Bar


T = TypeVar("T", contravariant=True)


class ProfileRepositoryServiceProtocol(Protocol[T]):
    async def get_profile(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetProfileResult | None: ...
