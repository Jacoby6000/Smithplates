# SQL plugin

Maven coordinate: `com.jacoby6000:smithy-stache-plugin:0.1.0`

Smithy build plugin (`smithy-stache`) and trait namespace for relational schema and repository codegen from Smithy models.

## Codegen pipeline

The plugin extracts **SQL IR** (tables, relationships, derived DML) from the Smithy model. SQL IR feeds **schema and migrations** directly. **SQL database service codegen** combines **database services and operations IR** (`@sqlService` contracts plus SQL IR) with **Mustache templates** to render target-language artifacts.

| Path | `smithy-build.json` config | Generated artifacts |
|------|---------------------------|---------------------|
| **Schema and migrations** | `smithy-stache.sql.<dialect>` with `enable: true` | Dialect-specific DDL (`.sql` migration files today; per-language migration engines planned in [#2](https://github.com/Jacoby6000/SmithyStache/issues/2)) |
| **SQL database service codegen** | `smithy-stache.sql.languageTargets` | Target-language query models, repository interfaces, dialect-specific implementations, and derived-query integration tests |

See [Architecture](../contributing/architecture.md) for the full pipeline diagram and implementation mapping.

## `smithy-stache` SQL outputs

| Config | Output |
|--------|--------|
| Enabled dialects (`sqlite`, `postgres`) | Per-dialect `.sql` files (SQL IR → dialect DDL): `CREATE TABLE`, indexes, enums, and a `-- Queries` section for derived DML |
| `languageTargets` | Mustache-rendered query models, `Protocol` interfaces, dialect-specific implementations, and derived-query test suites per `@sqlService` (service IR + SQL IR + templates) |

Trait definitions ship inside the plugin JAR: schema traits at `META-INF/smithy/stache.codegen.sql.smithy` (`smithy-sql-ir`) and query/service traits at `META-INF/smithy/stache.codegen.sql.service.smithy` (`smithy-sql-service-ir`). Typed Java trait classes register via `TraitService` SPI: schema traits under `com.jacoby6000.smithy.stache.sql.traits` (`smithy-sql-ir`) and query/service traits under `com.jacoby6000.smithy.stache.sql.service.traits` (`smithy-sql-service-ir`).

## Modeling conventions

- Annotate **structures** with `@sqlTable`; put `@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlIndex`, and column traits on **members**.
- Use flat `operations` lists on `@sqlService` services, not Smithy `resources` (resource properties cannot carry SQL member traits).
- Derive DML with `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, `@sqlDeriveSelectOne`, or `@sqlDeriveSelect` on operations; use `DerivedStruct` as derive input (and derive-select output).
- Bind repository SQL to service methods by matching operation shape ids on derive traits.

### Quick example

```smithy
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlForeignKey
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable

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
    output: String
}

@sqlService
service FooRepository {
    version: "1"
    operations: [GetFoo]
}
```

## SQL database service codegen

**SQL database service codegen** combines service IR, SQL IR-derived queries, and Mustache templates into target-language artifacts. Bundled Python templates live under `modules/smithy-sql-service-renderer/src/main/resources/sql-service-codegen/python/db/`. Configure `smithy-stache.sql.languageTargets` in `smithy-build.json` (see [Integration](integration.md)); bundled artifacts are selected from enabled dialects.

| Artifact | Pipeline stage |
|----------|----------------|
| `db/model/{{serviceFileName}}_models.py` | Target Language Query Models |
| `db/{{serviceFileName}}_protocol.py` | Target language interfaces |
| `db/<dialect>/{{serviceFileName}}_<driver>.py` | Dialect-specific implementations (interfaces + derived queries + templates) |
| `db/<dialect>/test_{{serviceFileName}}_derived_sql.py` | Test suite implementations (derived queries + abstract suites + templates; migration engine planned ([#2](https://github.com/Jacoby6000/SmithyStache/issues/2))) |

Layout for the bundled `db` service type:

```
db/
  model/models.mustache           → db/model/{{serviceFileName}}_models.py
  service_protocol.mustache       → db/{{serviceFileName}}_protocol.py
  sqlite/service_aiosqlite.mustache
  postgres/service_psycopg.mustache
  <implementation>/tests/…        → <testOutputDirectory>/db/<implementation>/test_*.py
```

`dialect` selects SQLite (`?` placeholders) or Postgres (`%s` placeholders unless `bindPlaceholderStyle` is overridden).

## Full reference

Trait tables, Smithy examples, template context fields, SPI entries, and Python validation test setup:

→ [`modules/smithy-stache-plugin/README.md`](../../modules/smithy-stache-plugin/README.md)

## Configuration

See [Integration](integration.md) for the `smithy-stache` plugin example and [`sql-plugin/README.md`](../../sql-plugin/README.md) for trait and template details.
