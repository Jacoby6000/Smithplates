# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import TypedDict


class Category(TypedDict):
    id: str
    name: str | None
    parent_category_id: str | None


class CategoryItem(TypedDict):
    id: str
    category_id: str | None
    label: str | None
