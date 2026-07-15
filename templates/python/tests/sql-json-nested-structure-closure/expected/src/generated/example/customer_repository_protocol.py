# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.customer_repository_models import (
    ContactInfo,
    Customer,
)

T = TypeVar("T", contravariant=True)


class CustomerRepositoryServiceProtocol(Protocol[T]):
    async def create_customer(
        self,
        name: str,
        contact: ContactInfo,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_customer(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> Customer | None: ...
    async def update_customer(
        self,
        name: str,
        contact: ContactInfo,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
    async def delete_customer(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool: ...
