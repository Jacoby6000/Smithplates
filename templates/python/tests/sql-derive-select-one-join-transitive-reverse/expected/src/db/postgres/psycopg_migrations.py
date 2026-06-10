# Generated from example#DepartmentRepository by sql-service-codegen. Do not edit by hand.
# Generated postgres migration service for sql-service-codegen. Do not edit by hand.
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

import psycopg

_MIGRATION_FILE_PATTERN = re.compile(r"^v(\d+).+\.sql$", re.IGNORECASE)
STATE_TABLE_NAME = "_smithplates_migrations"
STATE_TABLE_DDL = """CREATE TABLE IF NOT EXISTS _smithplates_migrations (
    version TEXT NOT NULL PRIMARY KEY,
    schema_hash TEXT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);"""


@dataclass(frozen=True)
class MigrationSpec:
    version: str
    version_number: int
    file_name: str
    schema_hash: str


MIGRATION_SPECS: tuple[MigrationSpec, ...] = (
    MigrationSpec(
        version="v1",
        version_number=1,
        file_name="v1_initial_schema.sql",
        schema_hash="7a780835388f00ce1d844726babebc3b0a1b0acfeca09c8118105cf3ec7f413f",
    ),
)


def parse_migration_version(file_name: str) -> int:
    match = _MIGRATION_FILE_PATTERN.match(file_name)
    if match is None:
        msg = f"Migration file must match v<number><suffix>.sql: {file_name}"
        raise ValueError(msg)
    return int(match.group(1))


class PsycopgMigrationService:
    def __init__(
        self,
        connection: psycopg.AsyncConnection,
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
        pending = await self._pending_migrations()
        if not pending:
            return False
        await self._apply_migration(pending[0])
        return True

    async def _ensure_state_table(self) -> None:
        await self._execute_script(STATE_TABLE_DDL)

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
        actual_hash = hashlib.sha256(sql_text.encode("utf-8")).hexdigest()
        if actual_hash != spec.schema_hash:
            msg = f"Schema hash mismatch for migration {spec.file_name}: expected {spec.schema_hash}, got {actual_hash}"
            raise ValueError(msg)
        await self._execute_script(sql_text)
        _ = await self._connection.execute(
            f"INSERT INTO {STATE_TABLE_NAME} (version, schema_hash) VALUES (%s, %s)",
            (spec.version, spec.schema_hash),
        )

    async def _execute_script(self, script: str) -> None:
        for statement in script.split(";"):
            ddl_statement = statement.strip()
            if ddl_statement:
                _ = await self._connection.execute(f"{ddl_statement};")

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
