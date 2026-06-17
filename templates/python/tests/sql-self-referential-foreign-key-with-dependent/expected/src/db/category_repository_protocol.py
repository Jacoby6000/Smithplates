# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.db.models.category_repository_models import (
    CategoryItem,
)

T = TypeVar("T", contravariant=True)


class CategoryRepositoryServiceProtocol(Protocol[T]):
    async def create_category(
        self,
        name: str,
        parent_category_id: str,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_category_item(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> CategoryItem | None: ...
