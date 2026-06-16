# Generated from petstore.db#PetRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from decimal import Decimal
from typing import cast, override

import psycopg
from generated.db.models.pet_repository_models import (
    Category,
    Owner,
    PetHighlight,
    PetProfile,
    PetTags,
    PostalAddress,
    Store,
)
from generated.db.pet_repository_protocol import (
    GetPetRecordResult,
    PetRepositoryServiceProtocol,
)
from generated.db.postgres.psycopg_transaction_run import run


class PetRepositoryPsycopgService(PetRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def create_pet_record(
        self,
        name: str,
        status: PetStatus,
        species: PetSpecies,
        category_id: str,
        owner_id: str,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes,
        adopted_at: datetime,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> str:
        async def execute() -> str:
            cur = await self._connection.execute(
"""INSERT INTO pets (name, status, species, category_id, owner_id, tag_count, tags, featured_attribute, photo, adopted_at) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id;"""
,
                (name, status, species, category_id, owner_id, tag_count, _json_bind_PetTags(tags), _json_bind_PetHighlight(featured_attribute), photo, _timestamp_bind_epoch_seconds(adopted_at)),
            )
            row = await cur.fetchone()
            if row is None:
                raise RuntimeError("INSERT RETURNING produced no row")
            return _read_str(row, 0)
        return await run(self._connection, transaction, execute)
    @override
    async def get_pet_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetPetRecordResult | None:
        async def execute() -> GetPetRecordResult | None:
            cur = await self._connection.execute(
"""SELECT pets.id, pets.name, pets.status, pets.species, pets.category_id, pets.owner_id, pets.tag_count, pets.tags, pets.featured_attribute, pets.photo, pets.adopted_at, pets.created_at, pets.updated_at, c.id AS c_id, c.name AS c_name, c.store_id AS c_store_id, s.id AS s_id, s.name AS s_name, o.id AS o_id, o.full_name AS o_full_name, o.mailing_address AS o_mailing_address, o.created_at AS o_created_at, pp.id AS pp_id, pp.biography AS pp_biography, pp.pet_id AS pp_pet_id
FROM pets AS pets
INNER JOIN categories AS c ON pets.category_id = c.id
INNER JOIN stores AS s ON c.store_id = s.id
LEFT JOIN owners AS o ON pets.owner_id = o.id
LEFT JOIN pet_profiles AS pp ON pets.id = pp.pet_id
WHERE pets.id = %s;"""
,
                (id,),
            )
            rows = await cur.fetchall()
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
                status=_read_str(row, 2),
                species=_read_int(row, 3),
                category_id=_read_str(row, 4),
                owner_id=_read_str(row, 5),
                tag_count=_read_int(row, 6),
                tags=_read_PetTags(row, 7),
                featured_attribute=_read_PetHighlight(row, 8),
                photo=_read_bytes(row, 9),
                adopted_at=_read_epoch_seconds(row, 10),
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
                owner=None if row[18] is None else Owner(
                    id=_read_str(row, 18),
                    full_name=_read_str(row, 19),
                    mailing_address=_read_PostalAddress(row, 20),
                    created_at=_read_datetime(row, 21),
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
        owner_id: str,
        tag_count: int,
        tags: PetTags,
        featured_attribute: PetHighlight,
        photo: bytes,
        adopted_at: datetime,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
"""UPDATE pets
SET name = %s, status = %s, species = %s, category_id = %s, owner_id = %s, tag_count = %s, tags = %s, featured_attribute = %s, photo = %s, adopted_at = %s, updated_at = CURRENT_TIMESTAMP
WHERE id = %s RETURNING updated_at;"""
,
                (name, status, species, category_id, owner_id, tag_count, _json_bind_PetTags(tags), _json_bind_PetHighlight(featured_attribute), photo, _timestamp_bind_epoch_seconds(adopted_at), id),
            )
            row = await cur.fetchone()
            return row is not None
        return await run(self._connection, transaction, execute)
    @override
    async def delete_pet_record(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> bool:
        async def execute() -> bool:
            cur = await self._connection.execute(
"""DELETE FROM pets WHERE id = %s RETURNING id;"""
,
                (id,),
            )
            row = await cur.fetchone()
            return row is not None
        return await run(self._connection, transaction, execute)
def _read_bytes(row: tuple[object, ...], index: int) -> bytes:
    return cast(bytes, row[index])

def _read_datetime(row: tuple[object, ...], index: int) -> datetime:
    return cast(datetime, row[index])

def _read_epoch_seconds(row: tuple[object, ...], index: int) -> datetime:
    return datetime.fromtimestamp(float(cast(Decimal, row[index])), tz=timezone.utc)

def _read_int(row: tuple[object, ...], index: int) -> int:
    return cast(int, row[index])

def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)

def _timestamp_bind_epoch_seconds(value: datetime) -> Decimal:
    if value.tzinfo is None:
        normalized = value.replace(tzinfo=timezone.utc)
    else:
        normalized = value.astimezone(timezone.utc)
    return Decimal(str(round(normalized.timestamp(), 3)))

def _json_bind_PetHighlight(value: PetHighlight) -> str:
    payload = { "name": cast(object, value.name), "color": cast(object, value.color) }
    return json.dumps(payload)


def _read_PetHighlight(row: tuple[object, ...], index: int) -> PetHighlight:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return PetHighlight(
        name=cast(str, data["name"]),
        color=cast(str, data["color"]),
    )

def _json_bind_PetTags(value: PetTags) -> str:
    payload = { "items": cast(object, value.items) }
    return json.dumps(payload)


def _read_PetTags(row: tuple[object, ...], index: int) -> PetTags:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return PetTags(
        items=cast(list[str], data["items"]),
    )

def _json_bind_PostalAddress(value: PostalAddress) -> str:
    payload = { "street": cast(object, value.street), "city": cast(object, value.city), "postal_code": cast(object, value.postal_code) }
    return json.dumps(payload)


def _read_PostalAddress(row: tuple[object, ...], index: int) -> PostalAddress:
    value = row[index]
    if isinstance(value, dict):
        data = cast(dict[str, object], value)
    else:
        data = cast(dict[str, object], json.loads(cast(str, value)))
    return PostalAddress(
        street=cast(str, data["street"]),
        city=cast(str, data["city"]),
        postal_code=cast(str, data["postal_code"]),
    )
