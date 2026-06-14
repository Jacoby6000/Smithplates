# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from category_repository_models import (
    Store,
)


@dataclass
class GetCategoryRecordResult:
    id: str
    name: str
    store_id: str
    store: Store

T = TypeVar("T", contravariant=True)


class CategoryRepositoryServiceProtocol(Protocol[T]):
    async def create_category_record(
        self,
        name: str,
        store_id: str,
        *,
        transaction: T | None = None,
    ) -> str:
        ...
    async def get_category_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetCategoryRecordResult | None:
        ...
