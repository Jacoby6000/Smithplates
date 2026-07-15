# Generated from petstore.db#PetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from typing import cast, override

import aiosqlite

from generated.petstore.db.models.pet_repository_models import (
    Category,
    CreatePetRecordOutput,
    DeletePetRecordOutput,
    Owner,
    PetHighlight,
    PetProfile,
    PetTags,
    PostalAddress,
    Store,
    UpdatePetRecordOutput,
)
from generated.petstore.db.pet_repository_protocol import (
    GetPetRecordResult,
    PetRepositoryServiceProtocol,
)
from generated.petstore.db.pet_species import PetSpecies
from generated.petstore.db.pet_status import PetStatus
from generated.petstore.db.sqlite.sqlite_transaction_run import run


class PetRepositoryAiosqliteService(PetRepositoryServiceProtocol[aiosqlite.Connection]):
    def __init__(self, connection: aiosqlite.Connection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str | None,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes | None,
        adopted_at: datetime | None,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> CreatePetRecordOutput:
        async def execute(conn: aiosqlite.Connection) -> CreatePetRecordOutput:
            cursor = await conn.execute(
                """INSERT INTO pets (name, status, species, category_id, owner_id, tag_count, tags, featured_attribute, photo, adopted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id;""",
                (
                    name,
                    status,
                    species,
                    category_id,
                    owner_id,
                    tag_count,
                    _json_bind_PetTags(tags),
                    _json_bind_PetHighlight(featured_attribute),
                    photo,
                    None if adopted_at is None else _timestamp_bind_epoch_seconds(adopted_at),
                ),
            )
            row = await cursor.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            named_row = _as_sqlite_named_row(cursor, row)
            return CreatePetRecordOutput(
                id=_read_str_col(named_row, "id"),
            )

        return await run(self._connection, transaction, execute)

    @override
    async def get_pet_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> GetPetRecordResult | None:
        async def execute(conn: aiosqlite.Connection) -> GetPetRecordResult | None:
            cursor = await conn.execute(
                """SELECT pets.id, pets.name, pets.status, pets.species, pets.category_id, pets.owner_id, pets.tag_count, pets.tags, pets.featured_attribute, pets.photo, pets.adopted_at, pets.created_at, pets.updated_at, c.id AS c_id, c.name AS c_name, c.store_id AS c_store_id, s.id AS s_id, s.name AS s_name, o.id AS o_id, o.full_name AS o_full_name, o.mailing_address AS o_mailing_address, o.created_at AS o_created_at, pp.id AS pp_id, pp.biography AS pp_biography, pp.pet_id AS pp_pet_id
FROM pets AS pets
INNER JOIN categories AS c ON pets.category_id = c.id
INNER JOIN stores AS s ON c.store_id = s.id
LEFT JOIN owners AS o ON pets.owner_id = o.id
LEFT JOIN pet_profiles AS pp ON pets.id = pp.pet_id
WHERE pets.id = ?;""",
                (id,),
            )
            rows = list(await cursor.fetchall())
            if not rows:
                return None
            row = rows[0]
            pet_profiles: list[PetProfile] = []
            for joined_row in rows:
                if joined_row[22] is not None:
                    pet_profiles.append(
                        PetProfile(
                            id=_read_str(joined_row, 22),
                            biography=_read_str(joined_row, 23),
                            pet_id=_read_str(joined_row, 24),
                        )
                    )
            return GetPetRecordResult(
                id=_read_str(row, 0),
                name=_read_str(row, 1),
                status=PetStatus(_read_str(row, 2)),
                species=PetSpecies(_read_int(row, 3)),
                category_id=_read_str(row, 4),
                owner_id=None if row[5] is None else _read_str(row, 5),
                tag_count=_read_int(row, 6),
                tags=_read_PetTags(row, 7),
                featured_attribute=_read_PetHighlight(row, 8),
                photo=None if row[9] is None else _read_bytes(row, 9),
                adopted_at=None if row[10] is None else _read_epoch_seconds(row, 10),
                created_at=_read_datetime(row, 11),
                updated_at=_read_datetime(row, 12),
                category=Category(
                    id=_read_str(row, 13),
                    name=_read_str(row, 14),
                    store_id=_read_str(row, 15),
                ),
                store=Store(
                    id=_read_str(row, 16),
                    name=_read_str(row, 17),
                ),
                owner=None
                if row[18] is None
                else Owner(
                    id=None if row[18] is None else _read_str(row, 18),
                    full_name=None if row[19] is None else _read_str(row, 19),
                    mailing_address=None if row[20] is None else _read_PostalAddress(row, 20),
                    created_at=None if row[21] is None else _read_datetime(row, 21),
                ),
                pet_profiles=pet_profiles,
            )

        return await run(self._connection, transaction, execute)

    @override
    async def update_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str | None,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes | None,
        adopted_at: datetime | None,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> UpdatePetRecordOutput:
        async def execute(conn: aiosqlite.Connection) -> UpdatePetRecordOutput:
            cursor = await conn.execute(
                """UPDATE pets
SET name = ?, status = ?, species = ?, category_id = ?, owner_id = ?, tag_count = ?, tags = ?, featured_attribute = ?, photo = ?, adopted_at = ?, updated_at = CURRENT_TIMESTAMP
WHERE id = ? RETURNING updated_at;""",
                (
                    name,
                    status,
                    species,
                    category_id,
                    owner_id,
                    tag_count,
                    _json_bind_PetTags(tags),
                    _json_bind_PetHighlight(featured_attribute),
                    photo,
                    None if adopted_at is None else _timestamp_bind_epoch_seconds(adopted_at),
                    id,
                ),
            )
            row = await cursor.fetchone()
            return UpdatePetRecordOutput(
                updated=row is not None,
            )

        return await run(self._connection, transaction, execute)

    @override
    async def delete_pet_record(
        self,
        id: str,
        *,
        transaction: aiosqlite.Connection | None = None,
    ) -> DeletePetRecordOutput:
        async def execute(conn: aiosqlite.Connection) -> DeletePetRecordOutput:
            cursor = await conn.execute(
                """DELETE FROM pets WHERE id = ? RETURNING id;""",
                (id,),
            )
            row = await cursor.fetchone()
            return DeletePetRecordOutput(
                deleted=row is not None,
            )

        return await run(self._connection, transaction, execute)


def _map_to_PetHighlight(data: dict[str, object]) -> PetHighlight:
    return PetHighlight(
        name=cast(str, data["name"]),
        color=cast(str, data["color"]),
    )


def _dump_PetHighlight(value: PetHighlight) -> dict[str, object]:
    return {
        "name": value.name,
        "color": value.color,
    }


def _map_to_PetTags(data: dict[str, object]) -> PetTags:
    return PetTags(
        items=cast(list[str], data["items"]),
    )


def _dump_PetTags(value: PetTags) -> dict[str, object]:
    return {
        "items": value.items,
    }


def _map_to_PostalAddress(data: dict[str, object]) -> PostalAddress:
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
        postal_code=cast(str, data["postal_code"]),
    )


def _dump_PostalAddress(value: PostalAddress) -> dict[str, object]:
    return {
        "street": value.street,
        "city": value.city,
        "postal_code": value.postal_code,
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


def _read_bytes(row: tuple[object, ...] | sqlite3.Row, index: int) -> bytes:
    return cast(bytes, row[index])


def _read_datetime(row: tuple[object, ...] | sqlite3.Row, index: int) -> datetime:
    value = cast(str, row[index])
    if value.endswith("Z"):
        normalized = value[:-1] + "+00:00"
    elif " " in value and "T" not in value:
        normalized = value.replace(" ", "T", 1) + "+00:00"
    else:
        normalized = value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _read_epoch_seconds(row: tuple[object, ...] | sqlite3.Row, index: int) -> datetime:
    return datetime.fromtimestamp(cast(float, row[index]), tz=timezone.utc)


def _read_int(row: tuple[object, ...] | sqlite3.Row, index: int) -> int:
    return cast(int, row[index])


def _read_str(row: tuple[object, ...] | sqlite3.Row, index: int) -> str:
    return cast(str, row[index])


def _timestamp_bind_epoch_seconds(value: datetime) -> float:
    if value.tzinfo is None:
        normalized = value.replace(tzinfo=timezone.utc)
    else:
        normalized = value.astimezone(timezone.utc)
    return normalized.timestamp()


def _json_bind_PetHighlight(value: PetHighlight) -> str:
    return json.dumps(_dump_PetHighlight(value))


def _read_PetHighlight(row: tuple[object, ...] | sqlite3.Row, index: int) -> PetHighlight:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_PetHighlight(data)


def _json_bind_PetTags(value: PetTags) -> str:
    return json.dumps(_dump_PetTags(value))


def _read_PetTags(row: tuple[object, ...] | sqlite3.Row, index: int) -> PetTags:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_PetTags(data)


def _json_bind_PostalAddress(value: PostalAddress) -> str:
    return json.dumps(_dump_PostalAddress(value))


def _read_PostalAddress(row: tuple[object, ...] | sqlite3.Row, index: int) -> PostalAddress:
    data = cast(dict[str, object], json.loads(_read_str(row, index)))
    return _map_to_PostalAddress(data)


def _read_str_col(row: dict[str, object], column: str) -> str:
    return cast(str, row[column])
