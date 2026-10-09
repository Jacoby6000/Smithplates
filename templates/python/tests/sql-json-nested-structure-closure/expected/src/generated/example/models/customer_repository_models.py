# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class InnerChoiceContacts:
    contacts: dict[str, ContactInfo]


@dataclass
class InnerChoiceTimestamp:
    timestamp: datetime


InnerChoice = InnerChoiceContacts | InnerChoiceTimestamp


@dataclass
class NestedChoiceText:
    text: str


@dataclass
class NestedChoiceDeeper:
    deeper: InnerChoice


NestedChoice = NestedChoiceText | NestedChoiceDeeper


@dataclass
class Customer:
    id: str
    name: str
    contact: ContactInfo
    labels: list[str]
    contacts: list[ContactInfo]
    contact_map: dict[str, ContactInfo]
    alternate_labels: list[str] | None
    collection_only: list[CollectionOnlyValue]
    created_at: datetime


@dataclass
class CustomerNotFound:
    message: str


@dataclass
class CollectionOnlyValue:
    label: str
    contact: ContactInfo | None
    choice: NestedChoice
    history: list[InnerChoice] | None
    annotations: dict[str, ContactInfo] | None


@dataclass
class ContactInfo:
    email: str
    address: PostalAddress


@dataclass
class GeoCoordinates:
    lat: float
    lng: float
    recorded_at: datetime


@dataclass
class PostalAddress:
    street: str
    city: str
    coords: GeoCoordinates
