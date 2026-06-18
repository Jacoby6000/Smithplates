# Generated from petstore.db#PetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, TypeVar

from generated.db.models.pet_repository_models import (
    Category,
    CreatePetRecordOutput,
    DeletePetRecordOutput,
    Owner,
    PetHighlight,
    PetProfile,
    PetTags,
    Store,
    UpdatePetRecordOutput,
)


@dataclass
class GetPetRecordResult:
    id: str
    name: str
    status: PetStatus
    species: PetSpecies
    category_id: str
    owner_id: str | None
    tag_count: int
    tags: PetTags
    featured_attribute: PetHighlight
    photo: bytes | None
    adopted_at: datetime | None
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
        owner_id: str | None,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes | None,
        adopted_at: datetime | None,
        *,
        transaction: T | None = None,
    ) -> CreatePetRecordOutput: ...
    async def get_pet_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> GetPetRecordResult | None: ...
    async def update_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str | None,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes | None,
        adopted_at: datetime | None,
        id: str,
        *,
        transaction: T | None = None,
    ) -> UpdatePetRecordOutput: ...
    async def delete_pet_record(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> DeletePetRecordOutput: ...
