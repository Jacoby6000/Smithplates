# Integration tests

Schema-path integration tests for [`smithplates-plugin`](../../modules/smithplates-plugin/): Smithy models are extracted into SQL IR, dialect DDL is rendered, and the SQL is applied to real databases via [testcontainers-scala](https://github.com/testcontainers/testcontainers-scala/). These modules validate the **SQL IR → dialect-specific DDL → database schema integration tests** stage of the [codegen pipeline](architecture.md).

SQL database service codegen integration tests (derived-query pytest suites) are generated into consumer projects via `languageTargets.testOutputDir` from derived queries, abstract test-suite contracts, and SSP templates. Golden **render** output is compared in [`smithplates-plugin`](../../modules/smithplates-plugin/) by [`SqlServiceCodegenTemplateTestSuite`](../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/SqlServiceCodegenTemplateTestSuite.scala). Golden **execution** (ruff, mypy, pytest) runs via [`language-test-harnesses/python/run-tests.sh`](../../language-test-harnesses/python/run-tests.sh) against [`templates/python/expected-outputs/`](../../templates/python/expected-outputs/). A per-language migration engine ([#2](https://github.com/Jacoby6000/Smithplates/issues/2)) is planned as an additional input to generated test suites.

## Modules

| Module | Database | Container image |
|--------|----------|-----------------|
| [`smithplates-testkit`](../../modules/smithplates-testkit/) | — | Shared Smithy fixtures and dialect-neutral JDBC DDL helpers (`src/main`, consumed by renderer IT modules) |
| [`smithplates-sql-ddl-renderer-postgres-it`](../../modules/smithplates-sql-ddl-renderer-postgres-it/) | PostgreSQL 16 | `postgres:16-alpine` |
| [`smithplates-sql-ddl-renderer-sqlite-it`](../../modules/smithplates-sql-ddl-renderer-sqlite-it/) | SQLite 3 | `keinos/sqlite3` (CLI in Docker) |

Dialect-specific test helpers (`PostgresDdlSupport`, `SqliteContainerSupport`) live in the postgres and sqlite renderer IT modules, not in the testkit.

Postgres and SQLite renderer IT modules run integration tests via `test` (not SBT's legacy `it` / `IntegrationTest` scope). `smithplates-testkit` has no integration tests of its own unless added under `src/test`.

**Requires Docker** for the Postgres and SQLite modules.

## Run

From the Smithplates repository root:

```bash
sbtn smithplatesSqlDdlRendererPostgresIt/test
sbtn smithplatesSqlDdlRendererSqliteIt/test
```

Unit tests (no Docker):

```bash
sbtn smithplatesPlugin/test
sbtn smithplatesSqlServiceRenderer/test
```

Python language harness (requires [uv](https://docs.astral.sh/uv/); postgres variants require Docker):

```bash
./language-test-harnesses/python/run-tests.sh
./language-test-harnesses/python/run-tests.sh --implementation sqlite
```

## Coverage

- **Simple schema** — two tables, one foreign key; valid inserts succeed; orphan FK inserts fail.
- **Complex schema** — eight tables, ten foreign keys; multi-hop seed data and FK violation checks.
- **SQLite `@sqlVarchar`** — `CHECK(length(name) <= n)` rejects overlong values.
