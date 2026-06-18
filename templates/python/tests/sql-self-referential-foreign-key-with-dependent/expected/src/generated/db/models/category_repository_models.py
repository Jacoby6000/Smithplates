# Generated from example#CategoryRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Category:
    id: str
    name: str | None
    parent_category_id: str | None


@dataclass
class CategoryItem:
    id: str
    category_id: str | None
    label: str | None


@dataclass
class CreateCategoryResult:
    id: str
