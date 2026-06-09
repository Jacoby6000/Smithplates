# smithplates-plugin

Scala/SBT Smithy build plugin that extracts **SQL IR** from a Smithy model, renders dialect-specific DDL and schema integration tests, and runs **SQL database service codegen** by combining service IR, SQL IR, and Mustache templates into target-language repository artifacts and integration tests. See the [codegen pipeline](../docs/contributing/architecture.md) for the full architecture.

## Traits

Smithy trait IDL is packaged into the published plugin JAR from [`../smithy-sql-ir/`](../smithy-sql-ir/) (`META-INF/smithy/smithplates.codegen.sql.smithy`) and [`../smithy-sql-service-ir/`](../smithy-sql-service-ir/) (`META-INF/smithy/smithplates.codegen.sql.service.smithy`). Overview: [`docs/usage/sql-plugin.md`](../docs/usage/sql-plugin.md).

| Trait | Target | Maps to |
|-------|--------|---------|
| `@sqlTable(name: String)` | `structure` | `SqlTable` |
| `@sqlColumn(name: String?)` | `member` | `SqlColumn.name` override |
| `@sqlColumnIndex(index: Integer)` | `member` | Stable `@sqlTable` member ordering for DDL, queries, and codegen; unindexed members keep Smithy definition order; timestamp traits default to second-to-last / last |
| `@sqlVarchar(maxLength: Integer)` | `:test(string, member > string)` | `SqlColumnType.Varchar` on the member or on a string type alias |
| `@sqlUuid` | `:test(string, member > string)` | `SqlColumnType.Uuid` on the member or on a string type alias; never inferred from shape names |
| `@sqlJson` | `member` | `SqlColumnType.Json` for list, map, structure, or union members (explicit only; not inferred) |
| `@sqlPrimaryKey` | `member` | primary key column |
| `@sqlIndex(name: String?)` | `member` | `SqlIndex` |
| `@sqlUniqueIndex(name: String?)` | `member` | unique `SqlIndex`; on `@sqlForeignKey` members models a one-to-one relationship |
| `@sqlForeignKey(references: String, column: String?)` | `member` | `SqlForeignKey` with `ManyToOne` cardinality by default |
| `DerivedStruct` | `structure` | Sentinel empty shape for derive inputs and `@sqlDeriveSelect` output; codegen expands members |
| `@sqlDeriveInsert(targetTable: String)` | `operation` | INSERT derived from table members; input must be `DerivedStruct`; output is PK type or a RETURNING structure |
| `@sqlDeriveUpdate(targetTable: String)` | `operation` | UPDATE derived from table; input `DerivedStruct`; output `Boolean`; WHERE uses PKs, SET uses updatable columns |
| `@sqlDeriveDelete(targetTable: String)` | `operation` | DELETE derived from table; input `DerivedStruct`; output `Boolean`; WHERE uses PKs; SQL uses RETURNING on PK columns |
| `@sqlDeriveSelectOne(targetTable: String, joins: sqlSelectJoinList)` | `operation` | SELECT by PK; input `DerivedStruct`; output is the target `@sqlTable` when `joins` is empty, otherwise `DerivedStruct` with nested joined structures (singular for many-to-one/one-to-one, list for one-to-many). Singular nested members follow FK member requiredness (`@required` → required, otherwise optional). Join ON clauses resolve from the nearest prior joined table when no direct FK exists on the target table. |
| `@sqlDeriveSelect(…)` | `operation` | SELECT derived from trait lists; `projections` defaults to `"*"` (all columns from `from`/joins as `{alias}_{member}` fields) or an explicit list; input is an explicit structure; output must be `DerivedStruct` |
| `@sqlUpdate(tableRef: String)` | `structure` | UPDATE query for the referenced `@sqlTable` |
| `@sqlService` | `service` | SQL data-access service with flat `operations` only (no `resources`; see below) |
| `@sqlAutoUuid` | `member` | Database-generated UUID (implies `@sqlUuid`); omit from inserts; include PK on updates |
| `@sqlCreatedTimestamp` | `member` | Set on insert; omit from insert/update inputs |
| `@sqlUpdatedTimestamp` | `member` | Set on insert/update; omit from insert/update inputs |

`Document` and `Blob` members map to JSON and binary columns respectively. Smithy `enum` shapes map to `SqlColumnType.StringEnum` (Postgres `CREATE TYPE … AS ENUM`, SQLite `TEXT` with `CHECK`); `intEnum` maps to `SqlColumnType.IntEnum` (integer column with `CHECK` on allowed values). Lists, maps, and nested structures require `@sqlJson` on the member to store as JSON.

Auto-generated columns receive dialect-specific `DEFAULT` clauses in DDL. Generated SQL appends a `-- Queries` section with INSERT, UPDATE, DELETE, and SELECT statements using `$n` placeholders (Postgres) or `?` (SQLite). `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, and `@sqlDeriveSelectOne` use `input: DerivedStruct`. `@sqlDeriveSelect` uses an explicit input structure for bind parameters and `output: DerivedStruct` for projection-derived result fields. `projections` defaults to `"*"`, expanding every column from `from` and joined tables as explicit `{alias}_{member}` result fields; use an explicit projection list for aggregates, `groupBy`, `having`, or `orderBy`. Use `from: { table: ShapeId, alias: String? }` for the primary table; joins accept `tableAlias`. Reference bind parameters as `input.memberName` and table columns as `alias.columnName` (or bare column names when unique). Filter conditions use `{ left, operator, right }`; WHERE may reference table columns only; HAVING may reference projections (including aggregates).

```smithy
@sqlTable(name: "foos")
structure Foo {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @sqlForeignKey(references: "example#Bar")
    bar_id: String
    @sqlVarchar(maxLength: 128)
    name: String
    @sqlIndex(name: "idx_foos_created_at")
    @sqlCreatedTimestamp
    created_at: Timestamp
    @sqlUpdatedTimestamp
    updated_at: Timestamp
}

use smithplates.codegen.sql#DerivedStruct

@sqlDeriveInsert(targetTable: "example#Foo")
operation CreateFoo {
    input: DerivedStruct
    output: String
}

@sqlDeriveUpdate(targetTable: "example#Foo")
operation UpdateFoo {
    input: DerivedStruct
    output: Boolean
}

@sqlDeriveDelete(targetTable: "example#Foo")
operation DeleteFoo {
    input: DerivedStruct
    output: Boolean
}

@sqlDeriveSelectOne(targetTable: "example#Foo")
operation GetFoo {
    input: DerivedStruct
    output: Foo
}

structure ListFoosInput {
    bar_id: String
    minCount: Integer
}

@sqlDeriveSelect(
    from: { table: "example#Foo", alias: "f" },
    joins: [{ table: "example#Bar", type: "inner", tableAlias: "b" }],
    projections: [
        { alias: "fooId", source: "f.id" },
        { alias: "barName", source: "b.name" },
        { alias: "fooCount", aggregate: "count", source: "f.id" }
    ],
    where: [{ left: "f.bar_id", operator: "=", right: "input.bar_id" }],
    groupBy: ["f.id", "b.name"],
    having: [{ left: "fooCount", operator: "=", right: "input.minCount" }],
    orderBy: [{ projection: "fooId", direction: "asc" }]
)
operation ListFoos {
    input: ListFoosInput
    output: DerivedStruct
}

@sqlUpdate(tableRef: "example#Foo")
structure FooUpdate {
    @required
    id: String
    @required
    name: String
}

@sqlService
service FooRepository {
    version: "1"
    operations: [FindFoo]
}

structure FindFooInput {
    @required
    id: String
}

structure FindFooOutput {
    @required
    name: String
}

@error("client")
structure FooNotFound {
    @required
    message: String
}

operation FindFoo {
    input: FindFooInput
    output: FindFooOutput
    errors: [FooNotFound]
}
```

`@sqlService` operations model repository methods (`input` → `output | errors`). The `smithplates` plugin renders Mustache templates (Scalate) into interface and model artifacts from each `@sqlService` when a `languageTargets` entry is configured. Annotate operations with `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, `@sqlDeriveSelectOne`, or `@sqlDeriveSelect` to derive SQL from the target table.

`@sqlService` services use flat `operations` lists, not Smithy `resources`. The natural mapping would be one table per resource, but resource `properties` cannot carry SQL traits on members (`@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlColumn`, …). Tables stay as annotated `@sqlTable` structures; duplicating that metadata elsewhere for resources would hurt authoring UX without much gain.

## Build and test

```bash
sbtn test publishM2
```

Tests load the same packaged traits via `SqlTestModelLoader` from the compile classpath. Build inline models with [`SqlTestModelBuilder.assemble`](src/test/scala/com/jacoby6000/smithplates/sql/SqlTestModelBuilder.scala); do not duplicate trait definitions.

## Plugin layout

| Package | Responsibility |
|---------|----------------|
| `com.jacoby6000.smithplates.sql` | `SqlValidated` (`ValidatedNel[SqlSchemaError, *]`), schema IR extraction, table traits |
| `com.jacoby6000.smithplates.sql.traits` | Schema trait `TraitService` implementations (`smithy-sql-ir`, SPI-registered) |
| `com.jacoby6000.smithplates.sql.service` | Service/query IR, extractors, query rendering (`smithy-sql-service-ir`) |
| `com.jacoby6000.smithplates.sql.shared` | `SqlSchemaDdlRenderer` (schema DDL); dialect renderers implement this (`smithy-sql-ir`) |
| `com.jacoby6000.smithplates.sql.service.traits` | Query/service trait `TraitService` implementations (`smithy-sql-service-ir`, SPI-registered) |
| `com.jacoby6000.smithplates.sql.shared` | `SqlShared` (DDL rendering, enums, column lines), `SqlTableTree` (FK order), `DDLStatement` (schema DDL artifacts keyed by Smithy shape id) |
| `com.jacoby6000.smithplates.sql.sqlite` | SQLite column types and `CHECK` constraints |
| `com.jacoby6000.smithplates.sql.postgres` | Postgres column types |
| `com.jacoby6000.smithplates.sql.codegen` | Scalate Mustache rendering for `@sqlService` interface/model codegen |
| `com.jacoby6000.smithplates` | `smithplates` Smithy build plugin (SQL schema export and service codegen) |

Dialect renderer tests live under `sqlite` and `postgres` test packages (`SqliteRendererSpec`, `PostgresRendererSpec`).

Docker-backed schema-path integration tests (SQL IR → dialect DDL → real databases) live in [`../smithy-sql-postgres-renderer-it/`](../smithy-sql-postgres-renderer-it/) and [`../smithy-sql-sqlite-renderer-it/`](../smithy-sql-sqlite-renderer-it/).

## Schema DDL export

The **schema and migrations** path renders SQL IR to dialect-specific DDL plus derived DML. [`SmithplatesBuildPlugin`](src/main/scala/com/jacoby6000/smithplates/SmithplatesBuildPlugin.scala) calls [`DialectRenderers.render`](src/main/scala/com/jacoby6000/smithplates/DialectRenderers.scala), which composes dialect [`SqlSchemaDdlRenderer`](../../smithy-sql-ir/src/main/scala/com/jacoby6000/smithplates/sql/shared/SqlSchemaDdlRenderer.scala) output with service-IR query units, and writes the result to `migrationLocation`. Per-language migration engines are planned ([#2](https://github.com/Jacoby6000/Smithplates/issues/2)).

## SQL database service codegen

**SQL database service codegen** (database services and operations IR + SQL IR + Scalate SSP templates → query models, interfaces, dialect-specific implementations, and tests) is configured under `smithplates.sql.languageTargets` (see [`docs/usage/integration.md`](../docs/usage/integration.md)). [`SqlServiceCodegenRenderer`](../smithy-sql-service-renderer/src/main/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenRenderer.scala) (in `smithy-sql-service-renderer`) renders SSP templates with [Scalate](https://github.com/scalate/scalate) for each `@sqlService` in the model. Bundled Python templates live under [`../../templates/python/src/db/`](../../templates/python/src/db/) and are packaged as compile resources (default `classpath:`); bundled artifacts are selected from enabled dialects.

Template and output layout for the bundled `db` service type:

```
db/
  model/models.mustache                        → db/model/{{serviceFileName}}_models.py
  service_protocol.mustache                    → db/{{serviceFileName}}_protocol.py
  sqlite/service_aiosqlite.mustache            → db/sqlite/{{serviceFileName}}_aiosqlite.py
  sqlite/tests/service_derived_sql_integration_tests.mustache
                                               → <testOutputDirectory>/db/sqlite/test_{{serviceFileName}}_derived_sql.py
  postgres/service_psycopg.mustache            → db/postgres/{{serviceFileName}}_psycopg.py
  postgres/tests/service_derived_sql_integration_tests_postgres.mustache
                                               → <testOutputDirectory>/db/postgres/test_{{serviceFileName}}_derived_sql.py
```

Models and the service Protocol are shared once under `db/model/` and `db/`; driver-specific implementations live under `db/sqlite/` or `db/postgres/`. Integration test templates live under each implementation's `tests/` directory; rendered tests are written under the user-configured `testOutputDirectory` (required when any artifact has `kind: test`). See [`SqlServiceCodegenDbArtifacts`](src/main/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenDbArtifacts.scala) for the bundled artifact lists.

Each `@sqlService` produces one artifact set. Template context includes:

- **models** — every input, output, and error structure referenced by the service (including nested structures), with all members and Smithy optionality (`required` / `@required` vs optional)
- **operations** — one entry per service operation with flattened top-level input parameters (nested structures stay single typed arguments), output type, error types, and a precomputed response union (`Output | Error1 | …`; `@sqlDeriveSelectOne` uses `Output | None` and does not surface operation errors in generated Python)
- **sql** (when an operation matches a derived query by shape id) — rendered SQL statement, bind-parameter order, execution mode, and row-mapping metadata for `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, and `@sqlDeriveSelectOne`

Bundled templates (under `python/db/`):

| Kind | Template | Default output | Purpose |
|------|----------|----------------|---------|
| `src` | `db/model/models.mustache` | `db/model/{{serviceFileName}}_models.py` | `@dataclass` models (shared) |
| `src` | `db/service_protocol.mustache` | `db/{{serviceFileName}}_protocol.py` | async `Protocol` interface (shared) |
| `src` | `db/sqlite/service_aiosqlite.mustache` | `db/sqlite/{{serviceFileName}}_aiosqlite.py` | `aiosqlite.Connection` implementation (use with `"dialect": "sqlite"`) |
| `src` | `db/postgres/service_psycopg.mustache` | `db/postgres/{{serviceFileName}}_psycopg.py` | `psycopg.AsyncConnection` implementation (use with `"dialect": "postgres"`) |
| `test` | `db/sqlite/tests/service_derived_sql_integration_tests.mustache` | `db/sqlite/test_{{serviceFileName}}_derived_sql.py` | in-memory SQLite pytest lifecycle tests (under `testOutputDirectory`) |
| `test` | `db/postgres/tests/service_derived_sql_integration_tests_postgres.mustache` | `db/postgres/test_{{serviceFileName}}_derived_sql.py` | Testcontainers Postgres + psycopg pytest lifecycle tests (under `testOutputDirectory`) |

Enabled dialects (`sqlite`, `postgres`) select driver-specific templates and placeholder styles (`sqlite` → `?`, `postgres` → `%s`). Derived DML queries are rendered as segment lists with implied bind parameters between segments.

`@sqlJson` columns use per-type `_json_bind_*` / `_read_*` helpers: structures serialize as explicit field objects; unions use Smithy-style single-key discriminators (exactly one member key on read/write). `@sqlTable` row models treat `@required`, `@sqlPrimaryKey`, and database-managed members (`@sqlCreatedTimestamp`, `@sqlAutoUuid`, etc.) as non-optional; only members without those traits are typed as `T | None` without field defaults.

`outputFile` patterns support `{{serviceName}}`, `{{serviceClassName}}`, `{{serviceFileName}}`, `{{serviceNamespace}}`, `{{serviceShapeId}}`, and `{{serviceVersion}}`.

See [`SqlServiceCodegenRendererSpec`](src/test/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenRendererSpec.scala) for schema-level checks, and [`SqlServiceCodegenMustacheTemplateTestSuite`](src/test/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenMustacheTemplateTestSuite.scala) for golden Mustache output plus strict mypy/pyright/pytest validation (`python/db/sqlite` and `python/db/postgres` backends). Fixture layout and conventions: [`src/test/resources/mustache-template-tests/README.md`](src/test/resources/mustache-template-tests/README.md).

### Strict Python validation in Mustache tests

[`SqlServiceCodegenMustacheTemplateTestSuite`](src/test/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenMustacheTemplateTestSuite.scala) golden-compares rendered output, then [`PythonCodegenWorkspace`](src/test/scala/com/jacoby6000/smithplates/sql/codegen/PythonCodegenWorkspace.scala) writes each case under `target/sql-service-codegen-python-workspace/cases/<test-name>/` (`python/src/db/` + `python/test/db/<implementation>/`), runs strict **mypy** and **pyright** on src artifacts, and runs **pytest -m integration** on each generated `test_*_derived_sql.py` with `PYTHONPATH`/`MYPYPATH` covering `python/src/db/model`, `python/src/db/`, and the implementation directory. Postgres integration tests spin up `postgres:16-alpine` via `testcontainers[postgres]` (requires Docker). SQLite integration tests use in-memory `aiosqlite`. The uv project (`pyproject.toml`, `uv.lock`, `.venv`) lives in `target/sql-service-codegen-python-workspace/` and persists across test runs until `sbtn smithySqlServiceRenderer/clean`. Requires `uv` on `PATH`; postgres variants also require Docker.

After changing [`src/test/resources/sql-service-codegen-python-workspace/pyproject.toml`](src/test/resources/sql-service-codegen-python-workspace/pyproject.toml), refresh the lockfile:

```bash
cd modules/smithy-sql-service-renderer/src/test/resources/sql-service-codegen-python-workspace && uv lock
```

## Smithy integration

Register in `smithy-build.json` (see [`docs/usage/integration.md`](../docs/usage/integration.md)):

```json
"maven": {
  "dependencies": [
    "com.jacoby6000:smithplates-plugin:0.1.0"
  ]
},
"plugins": {
  "smithplates": {
    "sql": {
      "sqlite": {
        "enable": true,
        "migrationLocation": "sqlite.sql"
      },
      "postgres": {
        "enable": true,
        "migrationLocation": "postgres.sql"
      },
      "languageTargets": {
        "python": {
          "sourceOutputDir": "src/generated",
          "testOutputDir": "tests"
        }
      }
    }
  }
}
```

SPI entry: `com.jacoby6000.smithplates.SmithplatesBuildPlugin`

## Build-time generators

Contributor generator tasks are defined on **`smithplatesPlugin`** (also aliased on the root project). [`SmithplatesGenerators`](src/test/scala/com/jacoby6000/smithplates/generators/SmithplatesGenerators.scala) is the shared entrypoint for all build-time generators.

| Task | Usage |
|------|--------|
| `generateGoldenTemplatesFor` | `sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'` — writes rendered artifacts into `templates/<language>/expected-outputs/<case-name>/` |

Dialect renderer tests live under `sqlite` and `postgres` test packages (`SqliteRendererSpec`, `PostgresRendererSpec`).
