# Generated from example#ProfileRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import uuid
from typing import cast, override

import psycopg
from generated.example.models.profile_repository_models import (
    Bar,
)
from generated.example.postgres.psycopg_transaction_run import run
from generated.example.profile_repository_protocol import (
    GetProfileResult,
    ProfileRepositoryServiceProtocol,
)


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
            return cast(
                GetProfileResult,
                {
                    "id": _read_str(row, 0),
                    "display_name": None if row[1] is None else _read_str(row, 1),
                    "bar_id": _read_str(row, 2),
                    "bar": cast(
                        Bar,
                        {
                            "id": _read_str(row, 3),
                            "name": None if row[4] is None else _read_str(row, 4),
                        },
                    ),
                },
            )

        return await run(self._connection, transaction, execute)


def _read_str(row: tuple[object, ...], index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)
