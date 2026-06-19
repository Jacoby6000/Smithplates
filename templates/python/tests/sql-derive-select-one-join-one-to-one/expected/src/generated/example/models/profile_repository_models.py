# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Bar:
    id: str
    name: str | None


@dataclass
class Profile:
    id: str
    display_name: str | None
    bar_id: str
