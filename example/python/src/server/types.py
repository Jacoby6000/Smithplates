"""Smithy-aligned API types for the petstore HTTP contract."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import IntEnum, StrEnum
from typing import Any


class PetStatus(StrEnum):
    AVAILABLE = "available"
    PENDING = "pending"
    SOLD = "sold"


class PetSpecies(IntEnum):
    DOG = 1
    CAT = 2
    BIRD = 3
    REPTILE = 4


class OrderStatus(StrEnum):
    PLACED = "placed"
    APPROVED = "approved"
    DELIVERED = "delivered"


class OrderPriority(IntEnum):
    LOW = 1
    NORMAL = 2
    HIGH = 3


@dataclass(frozen=True)
class PostalAddress:
    street: str
    city: str
    postal_code: str


@dataclass(frozen=True)
class PetAttributeValue:
    """JSON union payload; exactly one field is set."""

    color: str | None = None
    weight_kg: float | None = None
    vaccinated: bool | None = None


@dataclass(frozen=True)
class PetAttribute:
    name: str
    value: PetAttributeValue


@dataclass(frozen=True)
class CreatePetRequest:
    name: str
    status: PetStatus
    species: PetSpecies
    category_id: str
    owner_id: str | None
    tag_count: int
    tags: list[str]
    attributes: list[PetAttribute]
    photo: bytes | None = None
    metadata: Any | None = None
    adopted_at: datetime | None = None


@dataclass(frozen=True)
class UpdatePetRequest:
    name: str
    status: PetStatus
    species: PetSpecies
    category_id: str
    owner_id: str | None
    tag_count: int
    tags: list[str]
    attributes: list[PetAttribute]
    photo: bytes | None = None
    metadata: Any | None = None
    adopted_at: datetime | None = None


@dataclass(frozen=True)
class CategorySummary:
    id: str
    name: str
    store_id: str


@dataclass(frozen=True)
class StoreSummary:
    id: str
    name: str


@dataclass(frozen=True)
class OwnerSummary:
    id: str
    full_name: str
    mailing_address: PostalAddress
    created_at: datetime


@dataclass(frozen=True)
class PetProfileSummary:
    id: str
    biography: str
    pet_id: str


@dataclass(frozen=True)
class PetDetail:
    id: str
    name: str
    status: PetStatus
    species: PetSpecies
    category_id: str
    owner_id: str | None
    tag_count: int
    tags: list[str]
    attributes: list[PetAttribute]
    photo: bytes | None
    metadata: Any | None
    adopted_at: datetime | None
    created_at: datetime
    updated_at: datetime
    category: CategorySummary
    store: StoreSummary
    owner: OwnerSummary | None
    profile: PetProfileSummary | None


@dataclass(frozen=True)
class CategoryDetail:
    id: str
    name: str
    store_id: str
    store: StoreSummary


@dataclass(frozen=True)
class PlaceOrderRequest:
    label: str
    status: OrderStatus
    priority: OrderPriority


@dataclass(frozen=True)
class OrderLineDetail:
    id: str
    order_id: str
    pet_id: str
    quantity: int
    unit_price_cents: int
    fulfillment: dict[str, Any]


@dataclass(frozen=True)
class OrderDetail:
    id: str
    label: str
    status: OrderStatus
    priority: OrderPriority
    created_at: datetime
    updated_at: datetime
    lines: list[OrderLineDetail]
