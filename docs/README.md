# SmithyStache documentation

SmithyStache publishes Smithy build plugins for SQL schema and repository codegen.

## Guides

| Document | Audience |
|----------|----------|
| [Getting started](getting-started.md) | Build, test, and publish plugin JARs locally |
| [Integration](integration.md) | Wire plugins into a consumer `smithy-build.json` |
| [Architecture](architecture.md) | Module layout, design, and toolchain pins |

## Plugins

| Document | Plugin |
|----------|--------|
| [SQL schema & service codegen](sql-plugin.md) | `com.jacoby6000:smithy-sql-plugin` |

## Testing

| Document | Topic |
|----------|-------|
| [Integration tests](integration-tests.md) | Docker-backed Postgres and SQLite end-to-end tests |

## Module reference

Detailed trait tables, Mustache template layout, and plugin SPI entries live in [`sql-plugin/README.md`](../sql-plugin/README.md).

Agent and contributor conventions are in [`AGENTS.md`](../AGENTS.md) at the repository root.
