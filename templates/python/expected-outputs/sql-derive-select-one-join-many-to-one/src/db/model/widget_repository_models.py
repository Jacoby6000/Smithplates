# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Category:
    id: str
    name: str | None

@dataclass
class Widget:
    id: str
    title: str | None
    category_id: str

@dataclass
class GetWidgetResult:
    id: str
    title: str
    category_id: str
    category: Category
