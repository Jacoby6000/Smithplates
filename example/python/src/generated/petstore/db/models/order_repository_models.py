# Generated from petstore.db#OrderRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from generated.petstore.db.order_priority import OrderPriority
from generated.petstore.db.order_status import OrderStatus
from generated.petstore.db.pet_species import PetSpecies
from generated.petstore.db.pet_status import PetStatus


@dataclass
class FulfillmentStatePending:
    pending: str


@dataclass
class FulfillmentStateShipped:
    shipped: datetime


@dataclass
class FulfillmentStateDelivered:
    delivered: datetime


FulfillmentState = FulfillmentStatePending | FulfillmentStateShipped | FulfillmentStateDelivered


@dataclass
class CreateOrderRecordOutput:
    id: str


@dataclass
class Category:
    id: str
    name: str
    store_id: str


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
    id: int
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
