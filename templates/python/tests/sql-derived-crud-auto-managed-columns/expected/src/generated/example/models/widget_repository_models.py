# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from datetime import datetime
from typing import TypedDict


class Widget(TypedDict):
    id: str
    foo: str | None
    bar: int | None
    created_at: datetime
    updated_at: datetime
