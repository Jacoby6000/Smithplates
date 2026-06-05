# Integration tests

End-to-end tests for [`smithy-stache-plugin`](../../sql-plugin/): Smithy models are extracted, dialect DDL is rendered, and the SQL is applied to real databases via [testcontainers-scala](https://github.com/testcontainers/testcontainers-scala/).

## Modules

| Module | Database | Container image |
|--------|----------|-----------------|
| [`sql-plugin-common-it`](../../sql-plugin-common-it/) | — | Shared Smithy fixtures and dialect-neutral JDBC DDL helpers (`src/main`, consumed by other IT modules) |
| [`sql-plugin-postgres-it`](../../sql-plugin-postgres-it/) | PostgreSQL 16 | `postgres:16-alpine` |
| [`sql-plugin-sqlite-it`](../../sql-plugin-sqlite-it/) | SQLite 3 | `keinos/sqlite3` (CLI in Docker) |

Dialect-specific test helpers (`PostgresDdlSupport`, `SqliteContainerSupport`) live in the postgres and sqlite IT modules, not in common-it.

Postgres and SQLite IT modules run integration tests via `test` (not SBT's legacy `it` / `IntegrationTest` scope). `sql-plugin-common-it` has no integration tests of its own unless added under `src/test`.

**Requires Docker** for the Postgres and SQLite modules.

## Run

From the SmithyStache repository root:

```bash
sbtn smithySqlPluginPostgresIt/test smithySqlPluginSqliteIt/test
```

Unit tests for the plugin (no Docker):

```bash
sbtn smithySqlPlugin/test
```

## Coverage

- **Simple schema** — two tables, one foreign key; valid inserts succeed; orphan FK inserts fail.
- **Complex schema** — eight tables, ten foreign keys; multi-hop seed data and FK violation checks.
- **SQLite `@sqlVarchar`** — `CHECK(length(name) <= n)` rejects overlong values.
