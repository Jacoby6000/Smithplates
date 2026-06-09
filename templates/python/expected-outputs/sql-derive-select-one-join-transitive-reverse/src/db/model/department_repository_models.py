# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Category:
    id: str
    name: str | None
    department_id: str | None

@dataclass
class Department:
    id: str
    name: str | None

@dataclass
class Widget:
    id: str
    title: str | None
    category_id: str | None
