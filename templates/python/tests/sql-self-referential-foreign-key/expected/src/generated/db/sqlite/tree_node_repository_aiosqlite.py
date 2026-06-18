# Generated from example#TreeNodeRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import sqlite3
from typing import cast, override

import aiosqlite
from generated.db.models.tree_node_repository_models import (
    CreateTreeNodeResult,
    TreeNode,
)
from generated.db.sqlite.sqlite_transaction_run import run
from generated.db.tree_node_repository_protocol import TreeNodeRepositoryServiceProtocol


class TreeNodeRepositoryAiosqliteService(TreeNodeRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_tree_node(
        self,
        label: str | None,
        parent_node_id: str | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreateTreeNodeResult:
        async def execute(conn: aiosqlite.Connection) -> CreateTreeNodeResult:
            cursor = await conn.execute(
                """INSERT INTO tree_nodes (label, parent_node_id) VALUES (?, ?) RETURNING id;""",
                (label, parent_node_id),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreateTreeNodeResult(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_tree_node(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> TreeNode | None:
        async def execute(conn: aiosqlite.Connection) -> TreeNode | None:
            cursor = await conn.execute(
                """SELECT tree_nodes.id, tree_nodes.label, tree_nodes.parent_node_id
FROM tree_nodes
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            return _TreeNode_row_factory(cursor, row)

        return await run(self._connection, transaction, execute)


def _TreeNode_row_factory(cursor: object, row: tuple[object, ...] | sqlite3.Row) -> TreeNode:
    return TreeNode(
        id=_read_str(row, 0),
        label=_read_str(row, 1),
        parent_node_id=_read_str(row, 2),
    )


def _as_sqlite_named_row(
    cursor: sqlite3.Cursor | aiosqlite.Cursor,
    row: tuple[object, ...] | sqlite3.Row,
) -> dict[str, object]:
    if type(row) is not tuple:
        named = cast(sqlite3.Row, row)
        return {key: cast(object, named[key]) for key in named}
    description = cast(tuple[tuple[object, ...], ...], cursor.description)
    return {cast(str, column[0]): row[index] for index, column in enumerate(description)}


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
