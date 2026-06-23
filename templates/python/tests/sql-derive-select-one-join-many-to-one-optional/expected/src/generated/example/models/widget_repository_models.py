# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import TypedDict


class Category(TypedDict):
    id: str
    name: str | None


class Widget(TypedDict):
    id: str
    title: str | None
    category_id: str | None
