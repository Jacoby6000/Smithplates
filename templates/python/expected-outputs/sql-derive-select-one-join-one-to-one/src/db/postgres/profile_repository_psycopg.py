# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from typing import override

from typing import cast
import uuid
import psycopg
from psycopg_transaction_run import run
from profile_repository_models import (
    Bar,
    Profile,
    GetProfileResult,
)
from profile_repository_protocol import ProfileRepositoryServiceProtocol


class ProfileRepositoryPsycopgService(ProfileRepositoryServiceProtocol[psycopg.AsyncTransaction]):
    def __init__(self, connection: psycopg.AsyncConnection) -> None:
        super().__init__()
        self._connection = connection

    @override
    async def get_profile(
        self,
        id: str,
        *,
        transaction: psycopg.AsyncTransaction | None = None,
    ) -> GetProfileResult | None:
        async def execute() -> GetProfileResult | None:
            cur = await self._connection.execute(
                """SELECT profiles.id, profiles.display_name, profiles.bar_id, b.id AS b_id, b.name AS b_name
FROM profiles AS profiles
INNER JOIN bars AS b ON profiles.bar_id = b.id
WHERE profiles.id = %s;""",
                (id,),
            )
            row = await cur.fetchone()
            if row is None:
                return None
            return GetProfileResult(
                id=_read_str(row, 0),
                display_name=_read_str(row, 1),
                bar_id=_read_str(row, 2),
                bar=Bar(
                    id=_read_str(row, 3),
                    name=_read_str(row, 4),
                ),
            )
        return await run(self._connection, transaction, execute)
def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
