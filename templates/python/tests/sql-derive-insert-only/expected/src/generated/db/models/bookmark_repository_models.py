# Generated from example#BookmarkRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Bookmark:
    id: str
    title: str | None


@dataclass
class CreateBookmarkResult:
    id: str
