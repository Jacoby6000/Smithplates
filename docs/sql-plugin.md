# SQL plugin

Maven coordinate: `com.jacoby6000:smithy-stache-plugin:0.1.0`

Smithy build plugin (`smithy-stache`) and trait namespace for relational schema and repository codegen from Smithy models.

## `smithy-stache` SQL outputs

| Config | Output |
|--------|--------|
| Enabled dialects (`sqlite`, `postgres`) | Per-dialect `.sql` files: `CREATE TABLE`, indexes, enums, and a `-- Queries` section for derived DML |
| `languageTargets` | Mustache-rendered interface and model artifacts per `@sqlService` |

Trait definitions ship inside the plugin JAR at `META-INF/smithy/jacoby6000.codegen.sql.smithy`. Typed Java trait classes register via `TraitService` SPI under `com.jacoby6000.smithy.sql.traits`.

## Modeling conventions

- Annotate **structures** with `@sqlTable`; put `@sqlPrimaryKey`, `@sqlForeignKey`, `@sqlIndex`, and column traits on **members**.
- Use flat `operations` lists on `@sqlService` services, not Smithy `resources` (resource properties cannot carry SQL member traits).
- Derive DML with `@sqlDeriveInsert`, `@sqlDeriveUpdate`, `@sqlDeriveDelete`, `@sqlDeriveSelectOne`, or `@sqlDeriveSelect` on operations; use `DerivedStruct` as derive input (and derive-select output).
- Bind repository SQL to service methods by matching operation shape ids on derive traits.

### Quick example

```smithy
use jacoby6000.codegen.sql#DerivedStruct
use jacoby6000.codegen.sql#sqlForeignKey
use jacoby6000.codegen.sql#sqlPrimaryKey
use jacoby6000.codegen.sql#sqlTable

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

## Service codegen

Bundled Python templates live under `sql-plugin/src/main/resources/sql-service-codegen/python/db/`. Configure `smithy-stache.sql.languageTargets` in `smithy-build.json` (see [Integration](integration.md)); bundled artifacts are selected from enabled dialects.

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

→ [`sql-plugin/README.md`](../sql-plugin/README.md)

## Configuration

See [Integration](integration.md) for the `smithy-stache` plugin example and [`sql-plugin/README.md`](../sql-plugin/README.md) for trait and template details.
