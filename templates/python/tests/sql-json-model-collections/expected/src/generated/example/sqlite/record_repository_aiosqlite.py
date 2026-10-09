# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from typing import cast, override

import aiosqlite
from generated.example.models.record_repository_models import (
    Branch,
    Choice,
    ChoiceBranch,
    ChoiceText,
    Leaf,
    Record,
)
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from generated.example.sqlite.sqlite_transaction_run import run


class RecordRepositoryAiosqliteService(RecordRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_record(
        self,
        leaves: dict[str, Leaf],
        choices: list[Choice],
        groups: dict[str, list[Branch]] | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> str:
        async def execute(conn: aiosqlite.Connection) -> str:
            cursor = await conn.execute(
                """INSERT INTO records (leaves, choices, groups) VALUES (?, ?, ?) RETURNING id;""",
                (
                    _json_bind_collection_4d61705b537472696e672c204c6561665d(leaves),
                    _json_bind_collection_4c6973745b43686f6963655d(choices),
                    _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(groups),
                ),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> Record | None:
        async def execute(conn: aiosqlite.Connection) -> Record | None:
            cursor = await conn.execute(
                """SELECT records.id, records.leaves, records.choices, records.groups
FROM records
WHERE id = ?;""",
                (id,),
            )
            row = await cursor.fetchone()
            if row is None:
                return None
            named_row = _as_sqlite_named_row(cursor, row)
            return Record(
                id=_read_str_col(named_row, "id"),
                leaves=_read_collection_4d61705b537472696e672c204c6561665d_col(named_row, "leaves"),
                choices=_read_collection_4c6973745b43686f6963655d_col(named_row, "choices"),
                groups=None
                if named_row["groups"] is None
                else _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d_col(named_row, "groups"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def update_record(
        self,
        leaves: dict[str, Leaf],
        choices: list[Choice],
        groups: dict[str, list[Branch]] | None,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """UPDATE records
SET leaves = ?, choices = ?, groups = ?
WHERE id = ?;""",
                (
                    _json_bind_collection_4d61705b537472696e672c204c6561665d(leaves),
                    _json_bind_collection_4c6973745b43686f6963655d(choices),
                    _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(groups),
                    id,
                ),
            )
            return cursor.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> bool:
        async def execute(conn: aiosqlite.Connection) -> bool:
            cursor = await conn.execute(
                """DELETE FROM records WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return row is not None

        return await run(self._connection, transaction, execute)


def _map_to_Branch(data: dict[str, object]) -> Branch:
    return Branch(
        count=cast(int, data["count"]),
    )


def _dump_Branch(value: Branch) -> dict[str, object]:
    return {
        "count": value.count,
    }


def _map_to_Leaf(data: dict[str, object]) -> Leaf:
    return Leaf(
        text=cast(str, data["text"]),
    )


def _dump_Leaf(value: Leaf) -> dict[str, object]:
    return {
        "text": value.text,
    }


def _map_to_Choice(data: dict[str, object]) -> Choice:
    present = [key for key in ("branch", "text") if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown Choice discriminator: {sorted(data.keys())}")
    if "branch" in data:
        return ChoiceBranch(
            branch=_map_to_Branch(cast(dict[str, object], data["branch"])),
        )
    if "text" in data:
        return ChoiceText(
            text=cast(str, data["text"]),
        )
    raise ValueError(f"unknown Choice discriminator: {sorted(data.keys())}")


def _dump_Choice(value: Choice) -> dict[str, object]:
    if isinstance(value, ChoiceBranch):
        return {"branch": _dump_Branch(value.branch)}
    if isinstance(value, ChoiceText):
        return {"text": value.text}
    raise TypeError(f"unsupported Choice variant: {type(value)!r}")


def _json_bind_collection_4c6973745b43686f6963655d(value: list[Choice] | None) -> str | None:
    if value is None:
        return None
    return json.dumps([_dump_Choice(item) for item in value])


def _read_collection_4c6973745b43686f6963655d(row: tuple[object, ...] | sqlite3.Row, index: int) -> list[Choice]:
    data = json.loads(cast(str, row[index]))
    return [_map_to_Choice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b43686f6963655d_col(row: dict[str, object], column: str) -> list[Choice]:
    data = json.loads(cast(str, row[column]))
    return [_map_to_Choice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _json_bind_collection_4d61705b537472696e672c204c6561665d(value: dict[str, Leaf] | None) -> str | None:
    if value is None:
        return None
    return json.dumps({key: _dump_Leaf(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c204c6561665d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, Leaf]:
    data = json.loads(cast(str, row[index]))
    return {
        str(key): _map_to_Leaf(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204c6561665d_col(row: dict[str, object], column: str) -> dict[str, Leaf]:
    data = json.loads(cast(str, row[column]))
    return {
        str(key): _map_to_Leaf(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(
    value: dict[str, list[Branch]] | None,
) -> str | None:
    if value is None:
        return None
    return json.dumps({key: [_dump_Branch(item) for item in mapped_value] for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(
    row: tuple[object, ...] | sqlite3.Row, index: int
) -> dict[str, list[Branch]]:
    data = json.loads(cast(str, row[index]))
    return {
        str(key): [_map_to_Branch(cast(dict[str, object], item)) for item in cast(list[object], mapped_value)]
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d_col(
    row: dict[str, object], column: str
) -> dict[str, list[Branch]]:
    data = json.loads(cast(str, row[column]))
    return {
        str(key): [_map_to_Branch(cast(dict[str, object], item)) for item in cast(list[object], mapped_value)]
        for key, mapped_value in cast(dict[str, object], data).items()
    }


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


def _json_bind_Choice(value: Choice) -> str:
    return json.dumps(_dump_Choice(value))


def _read_Choice(row: tuple[object, ...] | sqlite3.Row, index: int) -> Choice:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_Choice(data)


def _json_bind_Branch(value: Branch) -> str:
    return json.dumps(_dump_Branch(value))


def _read_Branch(row: tuple[object, ...] | sqlite3.Row, index: int) -> Branch:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_Branch(data)


def _json_bind_Leaf(value: Leaf) -> str:
    return json.dumps(_dump_Leaf(value))


def _read_Leaf(row: tuple[object, ...] | sqlite3.Row, index: int) -> Leaf:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_Leaf(data)


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])


def _read_Choice_col(row: dict[str, object], column: str) -> Choice:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_Choice(data)


def _read_Branch_col(row: dict[str, object], column: str) -> Branch:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_Branch(data)


def _read_Leaf_col(row: dict[str, object], column: str) -> Leaf:
    data = cast(dict[str, object], json.loads(_read_str_col(row, column)))
    return _map_to_Leaf(data)
