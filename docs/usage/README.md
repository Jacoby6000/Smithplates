# Usage

Guides for **consuming** Smithplates plugins in your Smithy project.

## Start here

| Document | Topic |
|----------|-------|
| [Getting started](getting-started.md) | Minimal consumer path from Maven coordinate to generated output |
| [Configuration](configuration.md) | `smithplates` settings for SQL, HTTP, output roots, dialects, language targets, and templates |
| [Examples](examples.md) | Python petstore reference project and generated-artifact workflow |

## Feature guides

| Document | Topic |
|----------|-------|
| [SQL plugin](sql-plugin.md) | SQL traits, generated repositories, migration services, transactions, and Python DB behavior |
| [HTTP plugin](http-plugin.md) | `@httpService`, FastAPI/WebSocket output, Python and TypeScript clients, route groups, and problem details |
| [OpenAPI](openapi.md) | Smithplates HTTP projection transforms and OpenAPI Generator coordination |
| [Custom templates](custom-templates.md) | Bundled templates, custom `templateDirectory`, the `outputs.json` output deck, and template validation |
| [Limitations](limitations.md) | Current support boundaries and roadmap distinctions |

## Deep reference

| Document | Topic |
|----------|-------|
| [Integration](integration.md) | Full configuration walkthrough, output layout, OpenAPI coordination, and HTTP/SQL namespace separation |

Trait tables, Scalate SSP template context, and SPI details:

→ [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md)
