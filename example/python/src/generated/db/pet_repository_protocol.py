# Generated from petstore.db#PetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from pet_repository_models import (
    Category,
    Owner,
    PetHighlight,
    PetProfile,
    PetTags,
    Store,
)


@dataclass
class GetPetRecordResult:
    id: str
    name: str
    status: PetStatus
    species: PetSpecies
    category_id: str
    owner_id: str
    tag_count: int
    tags: PetTags
    featured_attribute: PetHighlight
    photo: bytes
    adopted_at: datetime
    created_at: datetime
    updated_at: datetime
    category: Category
    store: Store
    owner: Owner | None
    pet_profiles: list[PetProfile]

T = TypeVar("T", contravariant=True)


class PetRepositoryServiceProtocol(Protocol[T]):
    async def create_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes,
        adopted_at: datetime,
        *,
        transaction: T | None = None,
    ) -> str:
        ...
    async def get_pet_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetPetRecordResult | None:
        ...
    async def update_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes,
        adopted_at: datetime,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool:
        ...
    async def delete_pet_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> bool:
        ...
