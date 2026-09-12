# Generated from example#AccountRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.account_repository_models import (
    ExternalId,
    TenantId,
)

T = TypeVar("T", contravariant=True)


class AccountRepositoryServiceProtocol(Protocol[T]):
    async def create_account(
        self,
        tenant_id: TenantId | None,
        external_id: ExternalId | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
