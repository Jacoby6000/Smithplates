# SQL plugin

Maven coordinate: `com.jacoby6000:smithplates-plugin:<version>` (from `sbtn print smithplatesPlugin/version` after `publishM2`, or a published release/snapshot coordinate)

Smithy build plugin (`smithplates`) and trait namespace for relational schema and repository codegen from Smithy models.

## Codegen pipeline

The plugin extracts **SQL IR** (tables, relationships, derived DML) from the Smithy model. SQL IR feeds **schema and migrations** directly. **SQL database service codegen** combines **database services and operations IR** (`@sqlService` contracts plus SQL IR) with **Scalate SSP templates** to render target-language artifacts.

| Path | `smithy-build.json` config | Generated artifacts |
|------|---------------------------|---------------------|
| **Schema and migrations** | `smithplates.<language>.sql.<dialect>` with `enable: true` | Versioned migration `.sql` files under `migrationLocation` (initial `v1_initial_schema.sql` is full schema DDL) and generated per-dialect migration services (Python today) |
| **SQL database service codegen** | `smithplates.<language>.sql` | Target-language query models, repository interfaces, dialect-specific implementations, and derived-query integration tests |

See [Architecture](../contributing/architecture.md) for the full pipeline diagram and implementation mapping.

## `smithplates` SQL outputs

| Config | Output |
|--------|--------|
| Enabled dialects (`sqlite`, `postgres`) | Versioned migration `.sql` files under `migrationLocation` (SQL IR → dialect DDL); initial `v1_initial_schema.sql` contains full schema DDL (`CREATE TABLE`, indexes, enums, foreign-key constraints) |
| Language `sql` targets | Scalate SSP-rendered query models, `Protocol` interfaces, dialect-specific implementations, and derived-query test suites per `@sqlService` (service IR + SQL IR + templates) |

Trait definitions ship inside the plugin JAR: schema traits at `META-INF/smithy/smithplates.codegen.sql.smithy` (`smithplates-sql-ir`) and query/service traits at `META-INF/smithy/smithplates.codegen.sql.service.smithy` (`smithplates-sql-service-ir`). Typed Java trait classes register via `TraitService` SPI: schema traits under `com.jacoby6000.smithplates.sql.traits` (`smithplates-sql-ir`) and query/service traits under `com.jacoby6000.smithplates.sql.service.traits` (`smithplates-sql-service-ir`).

## Modeling conventions

- Annotate **structures** with `@sqlTable`; put `@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlIndex`, and column traits on **members**.
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

**SQL database service codegen** combines service IR, SQL IR-derived queries, and Scalate SSP templates into target-language artifacts. Bundled Python templates live under [`templates/python/src/db/`](../../templates/python/src/db/). Configure `smithplates.<language>.sql` in `smithy-build.json` (see [Integration](integration.md)); bundled artifacts are selected from enabled dialects.

| Artifact | Pipeline stage |
|----------|----------------|
| `db/model/{{serviceFileName}}_models.py` | Target Language Query Models |
| `db/{{enum_name_snake_case}}.py` | Python `StrEnum` / `IntEnum` classes for referenced Smithy `enum` / `intEnum` shapes |
| `db/{{serviceFileName}}_protocol.py` | Target language interfaces |
| `db/<dialect>/{{serviceFileName}}_<driver>.py` | Dialect-specific implementations (interfaces + derived queries + templates) |
| `db/<dialect>/<dialect>_migrations.py` | Dialect migration service: reads ordered `v<number>*.sql` files from a migrations directory, creates `_smithplates_migrations` state table, validates the live schema hash against the last recorded hash before applying pending migrations, applies one migration at a time, and records version plus a schema hash computed from database catalog metadata after each migration |
| `db/<dialect>/test_{{serviceFileName}}_derived_sql.py` | Derived-query integration tests (apply migrations via generated migration service) |
| `db/postgres/stubs/testcontainers/postgres.pyi` | Bundled mypy stubs for `testcontainers.postgres.PostgresContainer` in generated postgres integration tests (add `<testOutputDir>/db/postgres/stubs` to `mypy_path`) |

Layout for the bundled `db` service type:

```
db/
  model/models.ssp                → db/model/{{serviceFileName}}_models.py
  string_enum.ssp / int_enum.ssp  → db/{{enum_name_snake_case}}.py
  service_protocol.ssp            → db/{{serviceFileName}}_protocol.py
  sqlite/service_aiosqlite.ssp
  postgres/service_psycopg.ssp
  <implementation>/tests/…        → <testOutputDir>/db/<implementation>/test_*.py
```

`dialect` selects SQLite (`?` placeholders in generated Python) or Postgres (`%s` placeholders in generated Python service implementations). Build-time migration files contain dialect DDL. Generated column definitions include an `FK -> table (column)` comment for foreign-key columns. Postgres migrations emit foreign-key constraints as trailing `ALTER TABLE ... ADD CONSTRAINT` statements after table and index creation; SQLite migrations keep foreign keys inline in `CREATE TABLE` because SQLite cannot add table constraints after creation.

### Python row mapping (Postgres vs SQLite)

Generated Postgres (`*_psycopg.py`) implementations set **per-cursor** row factories (`class_row`, `dict_row`) on `psycopg.AsyncConnection.cursor(...)`. Each fetch is isolated to that cursor, so concurrent coroutines can share a connection safely.

Generated SQLite (`*_aiosqlite.py`) implementations use **mapper-style** row factories instead of assigning `connection.row_factory`. After `fetchone()` returns the default tuple row, codegen maps rows in-process—for example `_Widget_row_factory(cursor, row)` for scalar `selectOne`, or `_as_sqlite_named_row(cursor, row)` (a column-name `dict`) before column-keyed readers for JSON structures. The dict mapper avoids `sqlite3.Row(cursor, row)`, which requires a stdlib `sqlite3.Cursor` and does not accept `aiosqlite` cursors.

SQLite’s stdlib API only supports `row_factory` on the **connection**. Temporarily swapping it in async code is unsafe when multiple coroutines share one `aiosqlite.Connection`: a task can `await` between `execute` and `fetchone` while another task changes or restores `row_factory`, producing mis-typed rows or clobbering another task’s setting. Mapper-style factories keep the ergonomics of row factories without mutating shared connection state.

### Optional `transaction` parameter

Every generated service method ends with a keyword-only `transaction` parameter (default `None`). The shared `{{serviceFileName}}_protocol.py` is generic over a type parameter `T` (`class {{serviceClassName}}ServiceProtocol(Protocol[T])`) and declares `transaction: T | None`. Each dialect implementation binds `T` to that backend’s transaction handle type and implements the protocol as `{{serviceClassName}}ServiceProtocol[{{transactionTypeName}}]`:

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

See [Integration](integration.md) for the `smithplates` plugin example and [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md) for trait and template details.
