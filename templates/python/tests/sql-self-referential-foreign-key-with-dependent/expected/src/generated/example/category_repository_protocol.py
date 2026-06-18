# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.category_repository_models import (
    Category,
)

T = TypeVar("T", contravariant=True)


class CategoryRepositoryServiceProtocol(Protocol[T]):
    async def create_category(
        self,
        name: str | None,
        parent_category_id: str | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_category(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Category | None: ...
