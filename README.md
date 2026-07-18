# Smithplates

Pulling the AI Slop Machine lever to non-deterministically generate deterministic code-generators.

This project was inspired by OpenAPI Generator and some of my work at Disney. Outputs are built from smithy specifications, rendered with Scalate SSP templates.

> **Heavy construction.** Smithplates is early and actively evolving. APIs, plugin configuration, generated output, module layout, and documentation are all subject to frequent change — sometimes without a long deprecation window. If you try it today, expect churn: breaking changes, moving docs, and shifting golden-test expectations are normal for now. Pin versions if you experiment, and treat anything outside the documented quick-start paths as provisional.

## Architecture

The `smithplates` plugin extracts SQL and HTTP IR from the Smithy model, lowers shared shapes into a language-neutral codegen core (`NeutralType` / `ModelSet` / `CodegenPlanner`), then fans out into schema migrations, SQL database service codegen, and HTTP service/client codegen. Bundled artifact lists live in `outputs.json` decks beside Scalate SSP templates. SQL database service codegen combines `@sqlService` contracts with SQL IR and templates to produce query models, interfaces, dialect-specific implementations, migration runners, and test suites. HTTP codegen uses `@httpService` contracts for FastAPI servers (Python), HTTP clients (Python/httpx and TypeScript axios/fetch), shared models, WebSocket endpoints, and problem+json helpers.

<!-- architecture-pipeline.mmd:start -->
```mermaid
%%{init: {"flowchart": {"curve": "step"}}}%%
flowchart TD
    subgraph transform["Smithy Model Transformation"]
        SM["Smithy model"]
        SSP["smithplates-plugin"]
        ModelTransforms["Model transformations"]
        SQLIR["SQL schema IR"]
        SVCIR["SQL service/query IR"]
        HTTPIR["HTTP service IR"]
        Core["Neutral ModelSet / CodegenPlanner"]

        SM --> SSP
        SSP --> ModelTransforms
        ModelTransforms --> SQLIR
        SQLIR --> SVCIR
        ModelTransforms --> HTTPIR
        SQLIR --> Core
        SVCIR --> Core
        HTTPIR --> Core
    end

    subgraph sql["SQL Rendering"]
        DDL["Dialect-specific DDL"]
        Queries["Derived SQL query rendering"]
        Templates["DB Scalate SSP templates"]

        SQLIR --> DDL
        SVCIR --> Queries

        subgraph migration["Migration Engine"]
            MigrationSvc["Generated migration service"]
            PostgresMigrations["Postgres Migrations"]
            SqliteMigrations["SQLite Migrations"]

            DDL --> PostgresMigrations
            DDL --> SqliteMigrations
            PostgresMigrations --> MigrationSvc
            SqliteMigrations --> MigrationSvc
        end

        subgraph interfaces["SQL Service interfaces"]
            Models["DB models"]
            Protocols["Repository protocols"]
            PostgresImpl["SQL Service Postgres Implementations"]
            SqliteImpl["SQL Service SQLite Implementations"]

            SQLIR --> Models
            SVCIR --> Protocols
            Templates --> Models
            Templates --> Protocols
            Queries --> PostgresImpl
            Queries --> SqliteImpl
            Protocols --> PostgresImpl
            Protocols --> SqliteImpl
            MigrationSvc --> PostgresImpl
            MigrationSvc --> SqliteImpl
        end
    end

    subgraph http["HTTP Rendering"]
        HttpTemplates["HTTP Scalate SSP templates"]
        Routes["FastAPI routes + WebSockets"]
        HttpProtocols["Service protocols"]
        Clients["Python httpx / TypeScript clients"]
        Problems["Problem+JSON helpers"]

        Core --> Routes
        Core --> HttpProtocols
        Core --> Clients
        Core --> Problems
        HttpTemplates --> Routes
        HttpTemplates --> HttpProtocols
        HttpTemplates --> Clients
        HttpTemplates --> Problems
    end
```
<!-- architecture-pipeline.mmd:end -->

See [contributing architecture](docs/contributing/architecture.md) for module layout and implementation detail.

## AI Generated

AI Code in production is a recipe for disaster. Deterministically generated code is a huge boon, and this has been
demonstrably true for so long that code generation pipelines continue to be one of the best ways to produce client/server
interactions that are reliable. This project will test how far we can push the AI to generate generators that provide
higher quality output than the AI would output on its own.

## What works today

The `smithplates` plugin (`com.jacoby6000:smithplates-plugin`) is a Smithy build plugin. From a given Smithy specification it emits schema, SQL service, and HTTP service artifacts (see [Architecture](#architecture)):

| Path | Output | Supported today | Mechanism |
|------|--------|-----------------|-----------|
| **Schema and migrations** | Dialect-specific DDL (`.sql` migration files) | Postgres, SQLite | SQL IR → dialect renderers |
| **Schema and migrations** | Schema integration tests | Contributor modules | SQL IR → DDL applied to real databases (testcontainers) |
| **SQL database service codegen** | Target-language query models, repository interfaces, dialect-specific implementations, and migration runners | Python | Service IR + SQL IR + SSP templates under [`templates/python/`](templates/python/) |
| **SQL database service codegen** | Derived-query integration tests | Python (SQLite in-memory; Postgres via testcontainers) | Derived queries + generated migration runners + SSP templates |
| **HTTP service codegen** | FastAPI route modules, service protocols, app wiring, WebSocket routes, response helpers, and problem+json errors | Python | HTTP IR + planner decks + SSP templates under [`templates/python/src/http/`](templates/python/src/http/) |
| **HTTP client codegen** | Route-group clients, registries, operation bindings, WebSocket clients | Python (httpx); TypeScript (axios or fetch) | HTTP IR + planner decks + SSP templates under [`templates/python/`](templates/python/) / [`templates/typescript/`](templates/typescript/) |


All generated output is intended to be stand-alone and separate from your production code.  The Database Access Layer
generates an interface and automatically implements any derived queries, allowing you to provide your own alternative
implementations without overwriting any generated outputs.  These tools never output stubs that must be overwritten

## Where it is headed

- **Broader language coverage** — TypeScript HTTP clients ship today; SQL and HTTP *server* templates beyond Python, and additional client libraries, are still roadmap work
- **More database backends and access patterns** (sync/async drivers, connection pooling conventions, alternate placeholder styles)
- **Diff-based incremental migrations** beyond the current generated initial schema files and runtime migration runners
- **Custom language templates** — non-bundled languages can ship their own `templateDirectory` + `outputs.json` deck; contribute useful bundles upstream when you can


## Documentation

| Audience | Index |
|----------|-------|
| **Users** (consume plugins in your Smithy project) | [`docs/usage/`](docs/usage/) |
| **Contributors** (develop Smithplates) | [`docs/contributing/`](docs/contributing/) |

**Usage:** [Getting started](docs/usage/getting-started.md) · [Configuration](docs/usage/configuration.md) · [SQL plugin](docs/usage/sql-plugin.md) · [HTTP plugin](docs/usage/http-plugin.md) · [Examples](docs/usage/examples.md)

**Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md) · [Getting started](docs/contributing/getting-started.md) · [Architecture](docs/contributing/architecture.md) · [Testing](docs/contributing/testing.md) · [Template authoring](docs/contributing/template-authoring.md)

Conventions: [`AGENTS.md`](AGENTS.md) and [`.cursor/rules/`](.cursor/rules/)

## Quick start

Requires [sbtn](https://www.scala-sbt.org/) on `PATH` (`coursier install sbtn`).

```bash
sbtn publishM2
sbtn smithplatesPlugin/test
```

Pre-commit hooks (optional; `pre-commit install`):

```bash
pre-commit run --all-files
```

Runs `scalafmtAll`, `scalafixAll`, and `compile` on staged Scala/SBT changes, and checks reusable documentation components when applicable. See [Getting started](docs/contributing/getting-started.md#pre-commit-hooks).

Lint and format (also run in [CI](.github/workflows/ci.yml)):

```bash
sbtn scalafmtCheckAll
sbtn 'scalafixAll --check'
```

Docker-backed dialect tests:

```bash
sbtn smithplatesSqlDdlRendererPostgresIt/test
sbtn smithplatesSqlDdlRendererSqliteIt/test
```
