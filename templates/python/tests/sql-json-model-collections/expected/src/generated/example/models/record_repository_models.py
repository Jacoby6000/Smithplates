# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ChoiceBranch:
    branch: Branch


@dataclass
class ChoiceText:
    text: str


Choice = ChoiceBranch | ChoiceText


@dataclass
class Record:
    id: str
    leaves: dict[str, Leaf]
    choices: list[Choice]
    groups: dict[str, list[Branch]] | None


@dataclass
class Branch:
    count: int


@dataclass
class Leaf:
    text: str
