# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import TypedDict


class Bar(TypedDict):
    id: str
    name: str | None


class Profile(TypedDict):
    id: str
    display_name: str | None
    bar_id: str
