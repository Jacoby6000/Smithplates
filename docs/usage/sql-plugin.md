# SQL plugin

Maven coordinate: `com.jacoby6000:smithplates-plugin:<version>` (from `sbtn print smithplatesPlugin/version` after `publishM2`, or a published release/snapshot coordinate)

Smithy build plugin (`smithplates`) and trait namespace for relational schema and repository codegen from Smithy models.

## Codegen pipeline

The plugin extracts **SQL IR** (tables, relationships, derived DML) from the Smithy model. SQL IR feeds **schema and migrations** directly. **SQL database service codegen** lowers shared shapes into the language-neutral model set, expands the language's `outputs.json` deck with [`CodegenPlanner`](../contributing/architecture.md#language-neutral-codegen-planner-and-strategies), and renders Scalate SSP templates through `SqlNeutralServiceTemplateAttributes`.

| Path | `smithy-build.json` config | Generated artifacts |
|------|---------------------------|---------------------|
| **Schema and migrations** | `smithplates.<language>.sql.<dialect>` with `enable: true` | Versioned migration `.sql` files under `migrationLocation` (initial `v1_initial_schema.sql` is full schema DDL) and generated per-dialect migration services (Python today) |
| **SQL database service codegen** | `smithplates.<language>.sql` | Target-language query models, repository interfaces, dialect-specific implementations, and derived-query integration tests |

See [Architecture](../contributing/architecture.md) for the full pipeline diagram and implementation mapping. Settings live in [Configuration](configuration.md); this page focuses on modeling and generated SQL behavior.

## Trait cheat sheet

| Trait / shape | Apply to | Purpose |
|---------------|----------|---------|
| `@sqlTable` | structure | Marks a persistence table |
| `@sqlPrimaryKey` | member | Primary key column |
| `@sqlAutoUuid` | string member | DB-generated UUID PK (omit from inserts) |
| `@sqlAutoIncrement` | `Integer` member | Serial PK (SQLite `AUTOINCREMENT` / Postgres `IDENTITY`; omit from inserts) |
| `@sqlForeignKey` | member | FK to another `@sqlTable` |
| `@sqlIndex` / `@sqlUniqueIndex` | member | Secondary / unique indexes (unique FK → one-to-one) |
| `@sqlColumn` / `@sqlVarchar` / `@sqlUuid` / `@sqlJson` | member (or type) | Column naming and SQL types |
| `@sqlCreatedTimestamp` / `@sqlUpdatedTimestamp` | member | Managed timestamps (omit from insert/update inputs) |
| `DerivedStruct` | operation I/O | Sentinel expanded by derive traits |
| `@sqlDeriveInsert` / `Update` / `Delete` / `SelectOne` / `Select` | operation | Generate DML from the target table |
| `@sqlService` | service | Repository service with a flat `operations` list |

Full trait tables and SPI details: [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md).

## `smithplates` SQL outputs

| Config | Output |
|--------|--------|
| Enabled dialects (`sqlite`, `postgres`) | Versioned migration `.sql` files under `migrationLocation` (SQL IR → dialect DDL); initial `v1_initial_schema.sql` contains full schema DDL (`CREATE TABLE`, indexes, enums, foreign-key constraints) |
| Language `sql` targets | Scalate SSP-rendered query models, `Protocol` interfaces, dialect-specific implementations, and derived-query test suites per `@sqlService` (service IR + SQL IR + planner decks + templates) |

Trait definitions ship inside the plugin JAR: schema traits at `META-INF/smithy/smithplates.codegen.sql.smithy` (`smithplates-sql-ir`) and query/service traits at `META-INF/smithy/smithplates.codegen.sql.service.smithy` (`smithplates-sql-service-ir`). Typed Java trait classes register via `TraitService` SPI: schema traits under `com.jacoby6000.smithplates.sql.traits` (`smithplates-sql-ir`) and query/service traits under `com.jacoby6000.smithplates.sql.service.traits` (`smithplates-sql-service-ir`).

## Modeling conventions

- Annotate **structures** with `@sqlTable`; put `@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlIndex`, and column traits on **members**.
- Use `@sqlAutoIncrement` on an `Integer` primary-key member for database-generated serial columns (`INTEGER PRIMARY KEY AUTOINCREMENT` on SQLite, `GENERATED ALWAYS AS IDENTITY` on Postgres). Auto-increment columns are omitted from derived insert inputs; they must still appear on `@sqlUpdate` structures when they identify the row.
- Use `@sqlAutoUuid` for database-generated UUID primary keys (implies `@sqlUuid`; same insert/update rules as auto-increment).
- Use a **dedicated SQL namespace** (for example `example.db`) separate from HTTP API namespaces. Do not share shapes with `@httpService` models; see [Integration — HTTP and SQL model separation](integration.md#http-and-sql-model-separation).
- Use flat `operations` lists on `@sqlService` services, not Smithy `resources` (resource properties cannot carry SQL member traits).
- Derive DML with `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, `@sqlDeriveSelectOne`, or `@sqlDeriveSelect` on operations; use `DerivedStruct` as derive input (and derive-select output). `@sqlDeriveInsert` rejects target tables that participate in a cycle made entirely of `@required` foreign-key members, because safely inserting those rows requires deferred constraint evaluation. Cycles with at least one optional FK remain derivable.
- `@sqlDeriveSelectOne` accepts optional `joins`; when present, output must be `DerivedStruct` and codegen expands nested joined table structures from `@sqlForeignKey` cardinality (singular member for many-to-one/one-to-one, list member for one-to-many). Singular nested members are optional when the joining FK member is optional in Smithy, required when the FK member is `@required`. Each join after the first resolves its ON clause from the nearest prior joined table when no direct FK exists on the target table.
- Bind repository SQL to service methods by matching operation shape ids on derive traits.

### Quick example

```smithy
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable

@sqlTable(name: "foos")
structure Foo {
    @sqlPrimaryKey
    id: String
    @sqlForeignKey(references: "example#Bar")
    bar_id: String
    name: String
}

@sqlDeriveInsert(targetTable: "example#Foo")
operation CreateFoo {
    input: DerivedStruct
    output: CreateFooOutput
}

structure CreateFooOutput {
    @required
    id: String
}

@sqlService
service FooRepository {
    version: "1"
    operations: [CreateFoo]
}
```

## SQL database service codegen

**SQL database service codegen** combines service IR, SQL IR-derived queries, and Scalate SSP templates into target-language artifacts. Bundled Python templates live under [`templates/python/src/db/`](../../templates/python/src/db/) with artifact selection from [`templates/python/src/db/outputs.json`](../../templates/python/src/db/outputs.json). Generated **SQL models use dataclasses** with explicit JSON mapping helpers (HTTP models use Pydantic — see [HTTP plugin](http-plugin.md)). Configure `smithplates.<language>.sql` in `smithy-build.json` (see [Configuration](configuration.md)); bundled artifacts are selected from enabled dialects via the deck's `variants` keys (`sqlite`, `postgres`).

Paths are **namespace-aware**: artifacts land under `<sourceOutputDir>/<smithy namespace path>/…` (and the matching test root). Placeholders such as `{{smithyNamespaceDir}}` and `{{serviceModuleName}}` come from the language naming strategy. See [Configuration — Namespace-aware layout](configuration.md#namespace-aware-layout).

| Artifact (relative to `sourceOutputDir` / `testOutputDir`) | Pipeline stage | Deck id / notes |
|------------------------------------------------------------|----------------|-----------------|
| `{{smithyNamespaceDir}}/models/{{serviceModuleName}}_models.py` | Target language query models | `python.sql.db.models` |
| `{{smithyNamespaceDir}}/{{enumFileName}}.py` | Python `StrEnum` / `IntEnum` for referenced Smithy `enum` / `intEnum` shapes | **Scala side path** (`string_enum` / `int_enum` templates — not listed in `outputs.json`) |
| `{{smithyNamespaceDir}}/{{serviceModuleName}}_protocol.py` | Target language interfaces | `python.sql.db.service_protocol` |
| `{{smithyNamespaceDir}}/<dialect>/{{serviceModuleName}}_<driver>.py` | Dialect-specific implementations | `python.sql.db.sqlite.service` / `python.sql.db.postgres.service` |
| `{{smithyNamespaceDir}}/<dialect>/<dialect or driver>_migrations.py` | Dialect migration service: reads ordered `v<number>*.sql` files, creates `_smithplates_migrations`, validates schema hash, applies pending migrations | `python.sql.db.sqlite.migrations_service` / `python.sql.db.postgres.migrations_service` |
| `{{smithyNamespaceDir}}/<dialect>/*_transaction_run.py` | Shared transaction helpers (copied verbatim, non-`.ssp`) | `python.sql.db.sqlite.transaction_run` / `python.sql.db.postgres.transaction_run` |
| `{{smithyNamespaceDir}}/<dialect>/test_{{serviceModuleName}}_derived_sql.py` | Derived-query integration tests | `python.sql.db.*.integration_tests` under `testOutputDir` |
| `conftest.py` | Shared pytest bootstrap under `testOutputDir` | `python.sql.db.tests.conftest` |
| `{{smithyNamespaceDir}}/postgres/stubs/testcontainers/postgres.pyi` | Bundled mypy stubs for `testcontainers.postgres.PostgresContainer` | `python.sql.db.postgres.testcontainers_stub` (add that stubs dir to `mypy_path`) |

Layout for the bundled `db` template root:

```
db/
  outputs.json                    → deck: shared + sqlite/postgres variants
  models/models.ssp               → {{smithyNamespaceDir}}/models/{{serviceModuleName}}_models.py
  service_protocol.ssp            → {{smithyNamespaceDir}}/{{serviceModuleName}}_protocol.py
  string_enum.ssp / int_enum.ssp  → {{smithyNamespaceDir}}/{{enumFileName}}.py  (Scala side path)
  tests/conftest.py               → <testOutputDir>/conftest.py
  sqlite/service_aiosqlite.ssp
  sqlite/migrations_service.ssp
  sqlite/sqlite_transaction_run.py
  sqlite/tests/…
  postgres/service_psycopg.ssp
  postgres/migrations_service.ssp
  postgres/psycopg_transaction_run.py
  postgres/tests/…
  postgres/stubs/testcontainers/postgres.pyi
```

`dialect` selects SQLite (`?` placeholders in generated Python) or Postgres (`%s` placeholders in generated Python service implementations). Build-time migration files contain dialect DDL. Generated column definitions include an `FK -> table (column)` comment for foreign-key columns. Postgres migrations emit foreign-key constraints as trailing `ALTER TABLE ... ADD CONSTRAINT` statements after table and index creation; SQLite migrations keep foreign keys inline in `CREATE TABLE` because SQLite cannot add table constraints after creation.

### Python row mapping (Postgres vs SQLite)

Generated Postgres (`*_psycopg.py`) implementations set **per-cursor** row factories (`class_row`, `dict_row`) on `psycopg.AsyncConnection.cursor(...)`. Each fetch is isolated to that cursor, so concurrent coroutines can share a connection safely.

Generated SQLite (`*_aiosqlite.py`) implementations use **mapper-style** row factories instead of assigning `connection.row_factory`. After `fetchone()` returns the default tuple row, codegen maps rows in-process—for example `_Widget_row_factory(cursor, row)` for scalar `selectOne`, or `_as_sqlite_named_row(cursor, row)` (a column-name `dict`) before column-keyed readers for JSON structures. The dict mapper avoids `sqlite3.Row(cursor, row)`, which requires a stdlib `sqlite3.Cursor` and does not accept `aiosqlite` cursors.

SQLite’s stdlib API only supports `row_factory` on the **connection**. Temporarily swapping it in async code is unsafe when multiple coroutines share one `aiosqlite.Connection`: a task can `await` between `execute` and `fetchone` while another task changes or restores `row_factory`, producing mis-typed rows or clobbering another task’s setting. Mapper-style factories keep the ergonomics of row factories without mutating shared connection state.

### Optional `transaction` parameter

Every generated service method ends with a keyword-only `transaction` parameter (default `None`). The shared `{{serviceModuleName}}_protocol.py` is generic over a type parameter `T` (`class {{serviceClassName}}ServiceProtocol(Protocol[T])`) and declares `transaction: T | None`. Each dialect implementation binds `T` to that backend’s transaction handle type and implements the protocol as `{{serviceClassName}}ServiceProtocol[{{transactionTypeName}}]`:

| Dialect | `T` (transaction handle) | Pass when joining an outer transaction | Auto transaction when `None` |
|---------|--------------------------|----------------------------------------|------------------------------|
| Postgres (`*_psycopg.py`) | `psycopg.AsyncTransaction` | `psycopg.AsyncTransaction` from `async with connection.transaction() as tx:` (SQL still runs on `self._connection`; the handle signals an open transaction on that connection) | `psycopg_transaction_run.run` wraps the method `execute` lambda in `self._connection.transaction()` when `transaction` is `None` |
| SQLite (`*_aiosqlite.py`) | `aiosqlite.Connection` | `aiosqlite.Connection` after the caller has issued `BEGIN` (or `async with conn.transaction():`); SQL runs on that connection | `sqlite_transaction_run.run` issues `BEGIN` → `execute(conn)` → `commit` (or `rollback` on error) when `transaction` is `None` |

Pass the same transaction handle into multiple service calls to run them atomically. When `transaction` is omitted, each method still wraps its SQL in a single-operation transaction.

Generated `test_*_derived_sql.py` integration tests exercise this: `test_derived_sql_methods_transaction_commit` runs insert + select-one inside a caller-managed transaction and asserts rows persist after commit; `test_derived_sql_methods_transaction_rollback` asserts a rolled-back insert is not visible.

## Full reference

Trait tables, Smithy examples, template context fields, SPI entries, and Python validation test setup:

→ [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md)

## Configuration

SQL settings live under `smithplates.<language>.sql`. Enable dialects and declare `outputs` entries:

```json
{
  "plugins": {
    "smithplates": {
      "python": {
        "sql": {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          },
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        }
      }
    }
  }
}
```

Each `outputs` entry supports the same fields as HTTP: `sourceOutputDir` (required), `testOutputDir` (required), `services` (optional, omit to generate all `@sqlService` shapes), and `packageName` (optional). When the model has multiple `@sqlService` shapes for different deployables, use multiple entries:

```json
"outputs": [
  {
    "services": ["ServerDb"],
    "sourceOutputDir": "src/generated/server",
    "testOutputDir": "server/tests"
  },
  {
    "services": ["RunnerDb"],
    "sourceOutputDir": "src/generated/runner",
    "testOutputDir": "runner/tests"
  }
]
```

`migrationLocation` stays per-dialect and is separate from codegen output directories — it controls where the runtime migration service writes versioned `.sql` files, not where generated code lands.

See [Configuration](configuration.md) for the settings matrix and [Integration](integration.md) for a combined SQL + HTTP walkthrough. Trait and template details: [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md).
