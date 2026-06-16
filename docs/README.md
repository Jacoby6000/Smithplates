# Smithplates documentation

## [Usage](usage/)

For projects that depend on Smithplates plugins via `smithy build`:

- [Integration](usage/integration.md) — combined SQL and HTTP `smithy-build.json` walkthrough and output layout
- [Getting started](usage/getting-started.md) — minimal consumer path from plugin coordinate to generated output
- [Configuration](usage/configuration.md) — SQL and HTTP settings reference
- [SQL plugin](usage/sql-plugin.md) — traits, SQL IR, service IR, and generated artifacts
- [HTTP plugin](usage/http-plugin.md) — bundled Python/FastAPI service codegen
- [Examples](usage/examples.md) — Python petstore reference project
- [Limitations](usage/limitations.md) — shipped behavior boundaries and roadmap distinctions

## [Contributing](contributing/)

For Smithplates plugin development in this repository:

- [Getting started](contributing/getting-started.md) — build, test, lint, and publish locally
- [Architecture](contributing/architecture.md) — codegen pipeline, modules, packages, and design; reusable fragments in [`reusable-components/`](reusable-components/)
- [Testing](contributing/testing.md) — validation layers and focused commands
- [Template authoring](contributing/template-authoring.md) — SSP layout, golden tests, and precompilation
- [Integration tests](contributing/integration-tests.md) — dialect testcontainers coverage
- [Release](contributing/release.md) — publishing and generated example config workflow
- [Docs maintenance](contributing/docs-maintenance.md) — docs ownership and reusable component sync

Module reference (traits, templates, SPI): [`modules/smithplates-plugin/README.md`](../modules/smithplates-plugin/README.md)

Conventions: [`AGENTS.md`](../AGENTS.md)
