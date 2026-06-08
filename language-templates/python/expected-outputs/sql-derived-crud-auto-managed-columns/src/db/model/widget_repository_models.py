# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Widget:
    id: str
    foo: str | None
    bar: int | None
    created_at: datetime
    updated_at: datetime
