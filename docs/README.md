# Smithplates documentation

## [Usage](usage/)

For projects that depend on Smithplates plugins via `smithy build`:

- [Getting started](usage/getting-started.md) — minimal consumer path from plugin coordinate to generated output
- [Configuration](usage/configuration.md) — SQL and HTTP settings reference (canonical)
- [Integration](usage/integration.md) — combined SQL and HTTP walkthrough, OpenAPI coordination, namespace separation
- [SQL plugin](usage/sql-plugin.md) — traits, SQL IR, service IR, and generated artifacts
- [HTTP plugin](usage/http-plugin.md) — FastAPI/WebSocket server, Python and TypeScript clients
- [Custom templates](usage/custom-templates.md) — `outputs.json` decks, `templateDirectory`, additional decks
- [OpenAPI](usage/openapi.md) — projection transforms and OpenAPI Generator coordination
- [Examples](usage/examples.md) — Python and TypeScript petstore references
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
- [Roadmap](contributing/roadmap.md) — contributor-facing planning

Module reference (traits, templates, SPI): [`modules/smithplates-plugin/README.md`](../modules/smithplates-plugin/README.md)

Release history: [`CHANGELOG.md`](../CHANGELOG.md)

Conventions: [`AGENTS.md`](../AGENTS.md) and [`.cursor/rules/`](../.cursor/rules/)
