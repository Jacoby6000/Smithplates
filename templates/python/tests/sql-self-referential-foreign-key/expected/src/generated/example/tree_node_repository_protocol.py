# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import Protocol, TypeVar

from generated.example.models.tree_node_repository_models import (
    TreeNode,
)

T = TypeVar("T", contravariant=True)


class TreeNodeRepositoryServiceProtocol(Protocol[T]):
    async def create_tree_node(
        self,
        label: str | None,
        parent_node_id: str | None,
        *,
        transaction: T | None = None,
    ) -> str: ...
    async def get_tree_node(
        self,
        id: str,
        *,
        transaction: T | None = None,
    ) -> TreeNode | None: ...
