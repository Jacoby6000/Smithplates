# SmithyStache documentation

## [Usage](usage/)

For projects that depend on SmithyStache plugins via `smithy build`:

- [Integration](usage/integration.md) — wire `smithy-stache` into `smithy-build.json` (schema DDL and SQL database service codegen paths)
- [SQL plugin](usage/sql-plugin.md) — traits, SQL IR, service IR, and generated artifacts

## [Contributing](contributing/)

For SmithyStache plugin development in this repository:

- [Getting started](contributing/getting-started.md) — build, test, lint, and publish locally
- [Architecture](contributing/architecture.md) — codegen pipeline, modules, packages, and design; reusable fragments in [`reusable-components/`](reusable-components/)
- [Integration tests](contributing/integration-tests.md) — dialect testcontainers coverage

Module reference (traits, templates, SPI): [`modules/smithy-stache-plugin/README.md`](../modules/smithy-stache-plugin/README.md)

Conventions: [`AGENTS.md`](../AGENTS.md)
