# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.db.models.tree_node_repository_models import (
    TreeNode,
)
from generated.db.postgres.psycopg_transaction_run import run
from generated.db.tree_node_repository_protocol import TreeNodeRepositoryServiceProtocol
from psycopg.rows import class_row
from psycopg.types.string import TextLoader


class TreeNodeRepositoryPsycopgService(TreeNodeRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection
        connection.adapters.register_loader("uuid", TextLoader)

    @override
    async def create_tree_node(
        self,
        label: str | None,
        parent_node_id: str | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO tree_nodes (label, parent_node_id) VALUES (%s, %s) RETURNING id;""",
                (label, parent_node_id),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_tree_node(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> TreeNode | None:
        async def execute() -> TreeNode | None:
            async with self._connection.cursor(row_factory=class_row(TreeNode)) as cur:
                await cur.execute(
                    """SELECT tree_nodes.id, tree_nodes.label, tree_nodes.parent_node_id
FROM tree_nodes
WHERE id = %s;""",
                    (id,),
                )
                return await cur.fetchone()

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
