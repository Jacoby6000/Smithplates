# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

T = TypeVar("T", contravariant=True)


class BookmarkRepositoryServiceProtocol(Protocol[T]):
    async def create_bookmark(
        self,
        title: str | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
