# Generated from example#FlagRepository by sql-service-codegen. Do not edit by hand.
# Generated sqlite migration service for sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

import aiosqlite

_MIGRATION_FILE_PATTERN = re.compile(r"^v(\d+).+\.sql$", re.IGNORECASE)
STATE_TABLE_NAME = "_smithplates_migrations"
STATE_TABLE_DDL = """CREATE TABLE IF NOT EXISTS _smithplates_migrations (
    version TEXT NOT NULL PRIMARY KEY,
    schema_hash TEXT NOT NULL,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);"""


@dataclass(frozen=True)
class MigrationSpec:
    version: str
    version_number: int
    file_name: str


MIGRATION_SPECS: tuple[MigrationSpec, ...] = (
    MigrationSpec(
        version="v1",
        version_number=1,
        file_name="v1_initial_schema.sql",
    ),
)


def parse_migration_version(file_name: str) -> int:
    match = _MIGRATION_FILE_PATTERN.match(file_name)
    if match is None:
        msg = f"Migration file must match v<number><suffix>.sql: {file_name}"
        raise ValueError(msg)
    return int(match.group(1))


def _hash_schema_metadata(lines: list[str]) -> str:
    return hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest()


class SqliteMigrationService:
    def __init__(
        self,
        connection: aiosqlite.Connection,
        migrations_directory: Path,
    ) -> None:
        self._connection = connection
        self._migrations_directory = migrations_directory
        self._specs_by_file_name = {spec.file_name: spec for spec in MIGRATION_SPECS}

    async def migrate_all(self) -> None:
        while await self.migrate_next():
            pass

    async def migrate_next(self) -> bool:
        await self._ensure_state_table()
        await self._validate_schema_unchanged()
        pending = await self._pending_migrations()
        if not pending:
            return False
        await self._apply_migration(pending[0])
        return True

    async def _ensure_state_table(self) -> None:
        await self._connection.executescript(STATE_TABLE_DDL)

    async def _validate_schema_unchanged(self) -> None:
        stored_hash = await self._latest_stored_schema_hash()
        if stored_hash is None:
            return
        current_hash = await self._compute_schema_hash()
        if current_hash != stored_hash:
            msg = f"Database schema has drifted since the last migration: expected {stored_hash}, found {current_hash}"
            raise ValueError(msg)

    async def _pending_migrations(self) -> list[MigrationSpec]:
        applied_versions = await self._applied_versions()
        return [
            spec
            for spec in sorted(MIGRATION_SPECS, key=lambda migration: migration.version_number)
            if spec.version not in applied_versions
        ]

    async def _apply_migration(self, spec: MigrationSpec) -> None:
        sql_path = self._migrations_directory / spec.file_name
        if not sql_path.is_file():
            msg = f"Missing migration file: {sql_path}"
            raise FileNotFoundError(msg)
        sql_text = sql_path.read_text(encoding="utf-8")
        await self._connection.executescript(sql_text)
        schema_hash = await self._compute_schema_hash()
        _ = await self._connection.execute(
            f"INSERT INTO {STATE_TABLE_NAME} (version, schema_hash) VALUES (?, ?)",
            (spec.version, schema_hash),
        )

    async def _compute_schema_hash(self) -> str:
        cursor = await self._connection.execute(
            """
            SELECT type, name, sql
            FROM sqlite_master
            WHERE name NOT LIKE 'sqlite_%'
              AND name != ?
              AND sql IS NOT NULL
            ORDER BY type, name
            """,
            (STATE_TABLE_NAME,),
        )
        rows = await cursor.fetchall()
        lines = [f"{row[0]}|{row[1]}|{row[2]}" for row in rows]
        return _hash_schema_metadata(lines)

    async def _latest_stored_schema_hash(self) -> str | None:
        cursor = await self._connection.execute(
            f"""
            SELECT schema_hash
            FROM {STATE_TABLE_NAME}
            ORDER BY applied_at DESC
            LIMIT 1
            """
        )
        row = await cursor.fetchone()
        if row is None:
            return None
        return str(row[0])

    async def _applied_versions(self) -> set[str]:
        cursor = await self._connection.execute(f"SELECT version FROM {STATE_TABLE_NAME} ORDER BY version")
        rows = await cursor.fetchall()
        return {str(row[0]) for row in rows}

    def list_migration_files(self) -> list[Path]:
        if not self._migrations_directory.is_dir():
            msg = f"Migrations directory does not exist: {self._migrations_directory}"
            raise FileNotFoundError(msg)
        discovered: list[tuple[int, Path]] = []
        for path in self._migrations_directory.iterdir():
            if not path.is_file() or path.suffix.lower() != ".sql":
                continue
            file_name = path.name
            if file_name not in self._specs_by_file_name:
                msg = f"Unexpected migration file (not declared in MIGRATION_SPECS): {file_name}"
                raise ValueError(msg)
            discovered.append((parse_migration_version(file_name), path))
        return [path for _, path in sorted(discovered, key=lambda item: item[0])]
