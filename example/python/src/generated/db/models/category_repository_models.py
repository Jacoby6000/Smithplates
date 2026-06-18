# Generated from petstore.db#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import TypedDict


class FulfillmentStatePending(TypedDict):
    pending: str


class FulfillmentStateShipped(TypedDict):
    shipped: datetime


class FulfillmentStateDelivered(TypedDict):
    delivered: datetime


FulfillmentState = FulfillmentStatePending | FulfillmentStateShipped | FulfillmentStateDelivered


@dataclass
class Category:
    id: str
    name: str
    store_id: str


@dataclass
class CreateCategoryRecordOutput:
    id: str


@dataclass
class Order:
    id: str
    label: str
    status: OrderStatus
    priority: OrderPriority
    created_at: datetime
    updated_at: datetime


@dataclass
class OrderLine:
    id: str
    order_id: str
    pet_id: str
    quantity: int
    unit_price_cents: int
    fulfillment: FulfillmentState


@dataclass
class Owner:
    id: str
    full_name: str
    mailing_address: PostalAddress
    created_at: datetime


@dataclass
class Pet:
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


@dataclass
class PetHighlight:
    name: str
    color: str


@dataclass
class PetProfile:
    id: str
    biography: str
    pet_id: str


@dataclass
class PetTags:
    items: list[str]


@dataclass
class PostalAddress:
    street: str
    city: str
    postal_code: str


@dataclass
class Store:
    id: str
    name: str
