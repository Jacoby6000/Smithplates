# Generated from example#CustomerRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Customer:
    id: str
    name: str
    contact: ContactInfo
    created_at: datetime


@dataclass
class CustomerNotFound:
    message: str


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
