# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Flag:
    id: str
    label: str | None
    enabled: bool | None
