# Architecture

SmithyStache is an SBT multi-module project (**Scala 3.3.6**, strict compiler options) that publishes Smithy build plugins to Maven.

## Module graph

```
sql-plugin
    │
    ├───────────┬───────────┐
    │           │           │
common-it   postgres-it  sqlite-it
(library)     (test)       (test)
```

- **sql-plugin** — implements the `smithy-stache` Smithy build plugin; packages trait definitions at `META-INF/smithy/jacoby6000.codegen.sql.smithy`; registers typed Java trait classes under `com.jacoby6000.smithy.sql.traits` via Smithy `TraitService` SPI.
- **sql-plugin-common-it** — dialect-neutral fixtures and JDBC helpers in `src/main` (consumed by dialect IT modules).
- **sql-plugin-postgres-it** / **sql-plugin-sqlite-it** — end-to-end tests that apply generated DDL to real databases via [testcontainers-scala](https://github.com/testcontainers/testcontainers-scala/).

## SQL plugin design

### Validation

Model extraction and plugin settings validation use Cats **`ValidatedNel[SqlSchemaError, *]`** (`SqlValidated`) so errors accumulate across tables and members. [`SmithyStacheBuildPlugin`](../sql-plugin/src/main/scala/com/jacoby6000/smithy/stache/SmithyStacheBuildPlugin.scala) converts invalid results to a single exception at the Smithy build boundary. Do not use `for` on `Validated` (fail-fast); use `mapN` / `traverse`.

### Model extraction

[`SqlModelExtractor`](../sql-plugin/src/main/scala/com/jacoby6000/smithy/sql/SqlModelExtractor.scala) builds `SqlSchema.relationships` from `@sqlForeignKey` members: many-to-one by default, one-to-one when the FK column also has `@sqlUniqueIndex`.

[`SqlServiceExtractor`](../sql-plugin/src/main/scala/com/jacoby6000/smithy/sql/SqlServiceExtractor.scala) validates `@sqlService` operation contracts (input, output, errors) into `SqlSchema.services` for repository codegen. SQL query binding is opt-in via matching operation shape ids on derive traits.

### Rendering

Dialect renderers expose structured **`SqlRenderUnit`** values (DDL statements or DML queries keyed by Smithy shape id). Postgres `CREATE TYPE` units use the enum shape id; table DDL uses the `@sqlTable` structure id. [`SqlRenderOutput.format`](../sql-plugin/src/main/scala/com/jacoby6000/smithy/sql/shared/SqlRenderOutput.scala) joins units into exported `.sql` file text so unit tests can assert one query or one DDL artifact without parsing the full output.

### Package layout

| Package | Responsibility |
|---------|----------------|
| `com.jacoby6000.smithy.sql` | `SqlValidated`, model extraction, `DialectRenderer` dispatch |
| `com.jacoby6000.smithy.sql.traits` | Java `TraitService` implementations (SPI-registered) |
| `com.jacoby6000.smithy.sql.shared` | DDL rendering, FK ordering, query rendering, `SqlRenderOutput` |
| `com.jacoby6000.smithy.sql.sqlite` | SQLite column types and `CHECK` constraints |
| `com.jacoby6000.smithy.sql.postgres` | Postgres column types |
| `com.jacoby6000.smithy.sql.codegen` | Scalate Mustache rendering for `@sqlService` codegen |

## Dependencies

| Library | Version | Used for |
|---------|---------|----------|
| Smithy (`smithy-build`, `smithy-model`, `smithy-utils`) | 1.71.0 | Build plugins and trait model |
| Cats Core | 2.12.0 | `ValidatedNel` validation |
| Cats Effect | 3.7.0 | Available on all modules |
| Scalate | 1.10.1 | `sql-service-codegen` Mustache templates |

Synchronous extraction does not use `IO`.

## Toolchain

- **sbtn** — always use the SBT thin client from this repository; never plain `sbt` or `coursier launch org.scala-sbt:sbt-launch:…`.
- **Smithy models** — Smithy 2.0 IDL (`$version: "2.0"`).
- **Python renderer tests** — ephemeral uv workspace under `sql-plugin/target/sql-service-codegen-python-workspace/`; requires `uv` on `PATH`.
