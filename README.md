# SmithyStache

Smithy build plugins for SQL schema and repository codegen.

## Documentation

User-facing guides live in [`docs/`](docs/):

- [Getting started](docs/getting-started.md) — build, test, and `publishM2`
- [Integration](docs/integration.md) — wire plugins into a consumer `smithy-build.json`
- [Architecture](docs/architecture.md) — module layout and design
- [SQL plugin](docs/sql-plugin.md)
- [Integration tests](docs/integration-tests.md)

Contributor conventions: [`AGENTS.md`](AGENTS.md).

## Quick start

Requires [sbtn](https://www.scala-sbt.org/) on `PATH` (`coursier install sbtn`).

```bash
sbtn publishM2
sbtn smithySqlPlugin/test
```

Docker-backed dialect tests: `sbtn smithySqlPluginPostgresIt/test smithySqlPluginSqliteIt/test`
