# Getting started

## Prerequisites

| Tool | Purpose |
|------|---------|
| [sbtn](https://www.scala-sbt.org/) | SBT thin client (`coursier install sbtn` or `cs install sbtn`) |
| [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli.html) | Validate models and run `smithy build` in consumer projects (via Coursier) |
| [Docker](https://www.docker.com/) | Required only for dialect integration tests |
| [uv](https://docs.astral.sh/uv/) | Required only for `sql-service-codegen` Python renderer tests |
| [pre-commit](https://pre-commit.com/) | Optional; installs git hooks for fmt/fix/compile |

Assume `sbtn` is already on `PATH`. Run commands from the SmithyStache repository root.

## Modules

| Directory | Maven coordinate | Role |
|-----------|------------------|------|
| [`sql-plugin/`](../../sql-plugin/) | `com.jacoby6000:smithy-stache-plugin:0.1.0` | `smithy-stache` build plugin; SQL trait definitions |
| [`sql-plugin-common-it/`](../../sql-plugin-common-it/) | — | Shared integration-test fixtures (`src/main`) |
| [`sql-plugin-postgres-it/`](../../sql-plugin-postgres-it/) | — | Postgres integration tests (testcontainers-scala) |
| [`sql-plugin-sqlite-it/`](../../sql-plugin-sqlite-it/) | — | SQLite integration tests (testcontainers-scala) |

## Build and publish

Publish plugin JARs to the local Maven repository (`~/.m2`) before running `smithy build` in a consumer project:

```bash
sbtn publishM2
```

Consumer `smithy-build.json` files reference the coordinates above. Version numbers must match [`build.sbt`](../../build.sbt).

## Lint and format

Requires the sbt plugins in [`project/plugins.sbt`](../../project/plugins.sbt) (`sbtn` downloads them on first run):

```bash
sbtn scalafmtCheckAll
sbtn 'scalafixAll --check'
```

Apply fixes locally with `sbtn scalafmtAll` and `sbtn scalafixAll`.

## Pre-commit hooks

Install [pre-commit](https://pre-commit.com/) once, then enable hooks for this repository:

```bash
pre-commit install
```

On each commit that touches `*.scala` or `*.sbt`, hooks run (via [`scripts/pre-commit-scala.sh`](../../scripts/pre-commit-scala.sh)):

1. `sbtn scalafmtAll`
2. `sbtn scalafixAll`
3. `sbtn compile`

If scalafmt or scalafix change files, stage the updates and commit again. Run all hooks manually with `pre-commit run --all-files`.

## Unit tests

Requires [uv](https://docs.astral.sh/uv/) on `PATH` for Python codegen tests.

```bash
sbtn smithySqlPlugin/test
```

## Integration tests

Requires Docker:

```bash
sbtn smithySqlPluginPostgresIt/test smithySqlPluginSqliteIt/test
```

See [Integration tests](integration-tests.md) for coverage and module layout.

## Typical workflow

1. Change plugin sources in SmithyStache.
2. Run `sbtn publishM2`.
3. Run `smithy build` in the consumer Smithy project (models and `smithy-build.json` live in that repo).
4. Run unit tests (`sbtn smithySqlPlugin/test`) and, when SQL rendering changes, dialect IT modules.
