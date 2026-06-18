# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.db.models.bookmark_repository_models import (
    UpdateBookmarkResult,
)

T = TypeVar("T", contravariant=True)


class BookmarkRepositoryServiceProtocol(Protocol[T]):
    async def update_bookmark(
        self,
        title: str | None,
        id: str,
        *,
        transaction: T | None = None,
    ) -> UpdateBookmarkResult: ...
