# Smithplates documentation

## [Usage](usage/)

For projects that depend on Smithplates plugins via `smithy build`:

- [Integration](usage/integration.md) — wire `smithplates` into `smithy-build.json` (schema DDL and SQL database service codegen paths)
- [SQL plugin](usage/sql-plugin.md) — traits, SQL IR, service IR, and generated artifacts
- HTTP service codegen — bundled Python/FastAPI generation is documented in [Integration](usage/integration.md) until a dedicated HTTP guide is split out

## [Contributing](contributing/)

For Smithplates plugin development in this repository:

- [Getting started](contributing/getting-started.md) — build, test, lint, and publish locally
- [Architecture](contributing/architecture.md) — codegen pipeline, modules, packages, and design; reusable fragments in [`reusable-components/`](reusable-components/)
- [Integration tests](contributing/integration-tests.md) — dialect testcontainers coverage

Module reference (traits, templates, SPI): [`modules/smithplates-plugin/README.md`](../modules/smithplates-plugin/README.md)

Conventions: [`AGENTS.md`](../AGENTS.md)
