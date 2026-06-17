# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class TreeNode:
    id: str
    label: str | None
    parent_node_id: str | None
