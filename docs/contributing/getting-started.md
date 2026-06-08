# Getting started

## Prerequisites

| Tool | Purpose |
|------|---------|
| [sbtn](https://www.scala-sbt.org/) | SBT thin client (`coursier install sbtn` or `cs install sbtn`) |
| [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli.html) | Validate models and run `smithy build` in consumer projects (via Coursier) |
| [Docker](https://www.docker.com/) | Required only for dialect integration tests |
| [uv](https://docs.astral.sh/uv/) | Required for Python language test harness (`language-test-harnesses/python/`) |
| [pre-commit](https://pre-commit.com/) | Optional; installs git hooks for fmt/fix/compile |

Assume `sbtn` is already on `PATH`. Run commands from the SmithyStache repository root.

## Modules

| SBT project | Directory | Maven coordinate | Role |
|-------------|-----------|------------------|------|
| `smithyStachePlugin` | [`modules/smithy-stache-plugin/`](../../modules/smithy-stache-plugin/) | `com.jacoby6000:smithy-stache-plugin:0.1.0` | Published `smithy-stache` build plugin (orchestration only) |
| `smithySqlIr` | [`modules/smithy-sql-ir/`](../../modules/smithy-sql-ir/) | — | Schema IR, table extraction, shared DDL primitives |
| `smithySqlServiceIr` | [`modules/smithy-sql-service-ir/`](../../modules/smithy-sql-service-ir/) | — | Query/service IR, extractors |
| `smithySqlServiceQueryRenderer` | [`modules/smithy-sql-service-query-renderer/`](../../modules/smithy-sql-service-query-renderer/) | — | `SqlQueryRenderer` trait, parameterized statements, dialect-neutral query rendering |
| `smithySqlServiceQueryRendererPostgres` | [`modules/smithy-sql-service-query-renderer-postgres/`](../../modules/smithy-sql-service-query-renderer-postgres/) | — | Postgres `SqlQueryRenderer` |
| `smithySqlServiceQueryRendererSqlite` | [`modules/smithy-sql-service-query-renderer-sqlite/`](../../modules/smithy-sql-service-query-renderer-sqlite/) | — | SQLite `SqlQueryRenderer` |
| `smithySqlPostgresRenderer` | [`modules/smithy-sql-postgres-renderer/`](../../modules/smithy-sql-postgres-renderer/) | — | Postgres DDL renderer |
| `smithySqlSqliteRenderer` | [`modules/smithy-sql-sqlite-renderer/`](../../modules/smithy-sql-sqlite-renderer/) | — | SQLite DDL renderer |
| `smithySqlServiceRenderer` | [`modules/smithy-sql-service-renderer/`](../../modules/smithy-sql-service-renderer/) | — | Mustache service codegen (Python templates) |
| `smithyStacheTestkit` | [`modules/smithy-stache-testkit/`](../../modules/smithy-stache-testkit/) | — | Shared Smithy fixtures and JDBC DDL test helpers (`src/main`) |
| `smithySqlPostgresRendererIt` | [`modules/smithy-sql-postgres-renderer-it/`](../../modules/smithy-sql-postgres-renderer-it/) | — | Postgres renderer integration tests |
| `smithySqlSqliteRendererIt` | [`modules/smithy-sql-sqlite-renderer-it/`](../../modules/smithy-sql-sqlite-renderer-it/) | — | SQLite renderer integration tests |

## Build and publish

Publish plugin JARs to the local Maven repository (`~/.m2`) before running `smithy build` in a consumer project:

```bash
sbtn publishM2
```

Consumer `smithy-build.json` files reference `com.jacoby6000:smithy-stache-plugin`. Version numbers must match [`build.sbt`](../../build.sbt).

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
4. `scripts/sync_reusable_components.py --check` when `docs/reusable-components/` or embedded component markers change

If scalafmt or scalafix change files, stage the updates and commit again. After editing files under [`docs/reusable-components/`](../reusable-components/), run `scripts/sync_reusable_components.py` and re-stage the updated Markdown files. Run all hooks manually with `pre-commit run --all-files`.

## Linters

Run Scala and template-language linters/compilers (see [CONTRIBUTING.md](../../CONTRIBUTING.md) for Nix and Docker options):

```bash
./scripts/run-linters.sh
```

Subcommands: `scala` (scalafmt, scalafix, compile), `templates` (Python ruff + mypy).

## Tests

Run test suites only (linters are separate):

```bash
./scripts/run-tests.sh
```

Subcommands: `scala` (aggregated `sbtn test`), `templates` (Python pytest only).

## Unit tests

```bash
sbtn smithySqlIr/test
sbtn smithySqlServiceIr/test
sbtn smithySqlPostgresRenderer/test
sbtn smithySqlSqliteRenderer/test
sbtn smithySqlServiceRenderer/test
sbtn smithyStachePlugin/test
```

## Integration tests

Requires Docker:

```bash
sbtn smithySqlPostgresRendererIt/test
sbtn smithySqlSqliteRendererIt/test
```

Python generated-code integration tests (pytest against `templates/python/expected-outputs/`) require [uv](https://docs.astral.sh/uv/) and Docker for postgres variants:

```bash
./language-test-harnesses/python/run-tests.sh
```

See [Integration tests](integration-tests.md) for coverage and module layout.

## Typical workflow

1. Change plugin sources in SmithyStache.
2. Run `sbtn publishM2`.
3. Run `smithy build` in the consumer Smithy project (models and `smithy-build.json` live in that repo).
4. Run unit tests on affected modules and, when SQL rendering changes, dialect IT modules.
