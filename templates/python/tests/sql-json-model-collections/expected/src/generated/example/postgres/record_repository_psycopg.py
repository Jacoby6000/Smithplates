# Generated from example#RecordRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from typing import cast, override

import psycopg
from generated.example.models.record_repository_models import (
    Branch,
    Choice,
    ChoiceBranch,
    ChoiceText,
    Leaf,
    Record,
    SingletonChoice,
    SingletonChoiceLeaf,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.record_repository_protocol import RecordRepositoryServiceProtocol
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb


class RecordRepositoryPsycopgService(RecordRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_record(
        self,
        leaves: dict[str, Leaf],
        choices: list[Choice],
        singleton_choices: list[SingletonChoice],
        groups: dict[str, list[Branch]] | None,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
                """INSERT INTO records (leaves, choices, singleton_choices, groups) VALUES (%s, %s, %s, %s) RETURNING id;""",
                (
                    _json_bind_collection_4d61705b537472696e672c204c6561665d(leaves),
                    _json_bind_collection_4c6973745b43686f6963655d(choices),
                    _json_bind_collection_4c6973745b53696e676c65746f6e43686f6963655d(singleton_choices),
                    _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(groups),
                ),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)

        return await run(self._connection, transaction, execute)

    @override
    async def get_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> Record | None:
        async def execute() -> Record | None:
            async with self._connection.cursor(row_factory=dict_row) as cur:
                await cur.execute(
                    """SELECT records.id, records.leaves, records.choices, records.singleton_choices, records.groups
FROM records
WHERE id = %s;""",
                    (id,),
                )
                row = await cur.fetchone()
                if row is None:
                    return None
                return Record(
                    id=_read_str_col(row, "id"),
                    leaves=_read_collection_4d61705b537472696e672c204c6561665d_col(row, "leaves"),
                    choices=_read_collection_4c6973745b43686f6963655d_col(row, "choices"),
                    singleton_choices=_read_collection_4c6973745b53696e676c65746f6e43686f6963655d_col(
                        row, "singleton_choices"
                    ),
                    groups=None
                    if row["groups"] is None
                    else _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d_col(row, "groups"),
                )

        return await run(self._connection, transaction, execute)

    @override
    async def update_record(
        self,
        leaves: dict[str, Leaf],
        choices: list[Choice],
        singleton_choices: list[SingletonChoice],
        groups: dict[str, list[Branch]] | None,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """UPDATE records
SET leaves = %s, choices = %s, singleton_choices = %s, groups = %s
WHERE id = %s;""",
                (
                    _json_bind_collection_4d61705b537472696e672c204c6561665d(leaves),
                    _json_bind_collection_4c6973745b43686f6963655d(choices),
                    _json_bind_collection_4c6973745b53696e676c65746f6e43686f6963655d(singleton_choices),
                    _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(groups),
                    id,
                ),
            )
            return cur.rowcount > 0

        return await run(self._connection, transaction, execute)

    @override
    async def delete_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
                """DELETE FROM records WHERE id = %s RETURNING id;""",
                (id,),
            )
            row = await cur.fetchone()
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
    present = [key for key in ["branch", "text"] if key in data]
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


def _map_to_SingletonChoice(data: dict[str, object]) -> SingletonChoice:
    present = [key for key in ["leaf"] if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown SingletonChoice discriminator: {sorted(data.keys())}")
    if "leaf" in data:
        return SingletonChoiceLeaf(
            leaf=_map_to_Leaf(cast(dict[str, object], data["leaf"])),
        )
    raise ValueError(f"unknown SingletonChoice discriminator: {sorted(data.keys())}")


def _dump_SingletonChoice(value: SingletonChoice) -> dict[str, object]:
    if isinstance(value, SingletonChoiceLeaf):
        return {"leaf": _dump_Leaf(value.leaf)}
    raise TypeError(f"unsupported SingletonChoice variant: {type(value)!r}")


def _json_bind_collection_4c6973745b43686f6963655d(value: list[Choice] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([_dump_Choice(item) for item in value])


def _read_collection_4c6973745b43686f6963655d(row: tuple[object, ...], index: int) -> list[Choice]:
    data = row[index]
    return [_map_to_Choice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b43686f6963655d_col(row: dict[str, object], column: str) -> list[Choice]:
    data = row[column]
    return [_map_to_Choice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _json_bind_collection_4c6973745b53696e676c65746f6e43686f6963655d(
    value: list[SingletonChoice] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb([_dump_SingletonChoice(item) for item in value])


def _read_collection_4c6973745b53696e676c65746f6e43686f6963655d(
    row: tuple[object, ...], index: int
) -> list[SingletonChoice]:
    data = row[index]
    return [_map_to_SingletonChoice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _read_collection_4c6973745b53696e676c65746f6e43686f6963655d_col(
    row: dict[str, object], column: str
) -> list[SingletonChoice]:
    data = row[column]
    return [_map_to_SingletonChoice(cast(dict[str, object], item)) for item in cast(list[object], data)]


def _json_bind_collection_4d61705b537472696e672c204c6561665d(value: dict[str, Leaf] | None) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb({key: _dump_Leaf(mapped_value) for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c204c6561665d(row: tuple[object, ...], index: int) -> dict[str, Leaf]:
    data = row[index]
    return {
        str(key): _map_to_Leaf(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204c6561665d_col(row: dict[str, object], column: str) -> dict[str, Leaf]:
    data = row[column]
    return {
        str(key): _map_to_Leaf(cast(dict[str, object], mapped_value))
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _json_bind_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(
    value: dict[str, list[Branch]] | None,
) -> Jsonb | None:
    if value is None:
        return None
    return Jsonb({key: [_dump_Branch(item) for item in mapped_value] for key, mapped_value in value.items()})


def _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d(
    row: tuple[object, ...], index: int
) -> dict[str, list[Branch]]:
    data = row[index]
    return {
        str(key): [_map_to_Branch(cast(dict[str, object], item)) for item in cast(list[object], mapped_value)]
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_collection_4d61705b537472696e672c204c6973745b4272616e63685d5d_col(
    row: dict[str, object], column: str
) -> dict[str, list[Branch]]:
    data = row[column]
    return {
        str(key): [_map_to_Branch(cast(dict[str, object], item)) for item in cast(list[object], mapped_value)]
        for key, mapped_value in cast(dict[str, object], data).items()
    }


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _json_bind_Choice(value: Choice) -> str:
    return json.dumps(_dump_Choice(value))


def _read_Choice(row: tuple[object, ...], index: int) -> Choice:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Choice(data)


def _json_bind_SingletonChoice(value: SingletonChoice) -> str:
    return json.dumps(_dump_SingletonChoice(value))


def _read_SingletonChoice(row: tuple[object, ...], index: int) -> SingletonChoice:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_SingletonChoice(data)


def _json_bind_Branch(value: Branch) -> str:
    return json.dumps(_dump_Branch(value))


def _read_Branch(row: tuple[object, ...], index: int) -> Branch:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Branch(data)


def _json_bind_Leaf(value: Leaf) -> str:
    return json.dumps(_dump_Leaf(value))


def _read_Leaf(row: tuple[object, ...], index: int) -> Leaf:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Leaf(data)


def _read_str_col(row: dict[str, object], column: str) -> str:
    value = row[column]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)


def _read_Choice_col(row: dict[str, object], column: str) -> Choice:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Choice(data)


def _read_SingletonChoice_col(row: dict[str, object], column: str) -> SingletonChoice:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_SingletonChoice(data)


def _read_Branch_col(row: dict[str, object], column: str) -> Branch:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Branch(data)


def _read_Leaf_col(row: dict[str, object], column: str) -> Leaf:
    value = row[column]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return _map_to_Leaf(data)
