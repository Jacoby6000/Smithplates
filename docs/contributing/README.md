# Contributing

Guides for **developing** Smithplates itself.

## Start here

| Document | Topic |
|----------|-------|
| [Getting started](getting-started.md) | Prerequisites, build, lint, publish, and local workflow |
| [Testing](testing.md) | Validation entry points, Scala tests, golden tests, language harnesses, and examples |
| [Architecture](architecture.md) | Full codegen pipeline, module graph, package layout, and template precompilation |

## Change-path guides

| Document | Topic |
|----------|-------|
| [SQL architecture](sql-architecture.md) | SQL schema IR, service/query IR, DDL renderers, query renderers, and service renderer boundaries |
| [HTTP architecture](http-architecture.md) | HTTP IR, transforms, FastAPI rendering, plugin orchestration, and HTTP test coverage |
| [Template authoring](template-authoring.md) | SSP layout, fragments, bundled templates, golden tests, and precompilation |
| [Integration tests](integration-tests.md) | Docker-backed Postgres and SQLite schema-path integration tests |

## Maintainer guides

| Document | Topic |
|----------|-------|
| [Release](release.md) | Local publish, Maven Central, generated example configs, and release credentials |
| [Docs maintenance](docs-maintenance.md) | Usage vs contributing docs ownership, reusable components, and drift prevention |
| [Roadmap](roadmap.md) | Contributor-facing future work and roadmap hygiene |

Agent and coding conventions: [`AGENTS.md`](../../AGENTS.md) and [`.cursor/rules/`](../../.cursor/rules/) at the repository root.

CI: [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
